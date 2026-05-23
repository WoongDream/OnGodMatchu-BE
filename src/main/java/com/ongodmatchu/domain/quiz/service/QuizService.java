package com.ongodmatchu.domain.quiz.service;

import com.ongodmatchu.domain.comment.repository.QuizCommentRepository;
import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.dto.CategoryResponse;
import com.ongodmatchu.domain.quiz.dto.MyQuizListItemResponse;
import com.ongodmatchu.domain.quiz.dto.QuestionCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuestionResponse;
import com.ongodmatchu.domain.quiz.dto.QuizCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuizDetailResponse;
import com.ongodmatchu.domain.quiz.dto.QuizResponse;
import com.ongodmatchu.domain.quiz.dto.QuizSort;
import com.ongodmatchu.domain.quiz.dto.QuizUpdateRequest;
import com.ongodmatchu.domain.quiz.dto.VisibilityFilter;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizCategory;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.quiz.repository.QuizAttemptRepository;
import com.ongodmatchu.domain.quiz.repository.QuizAttemptRepository.QuizCorrectRateRow;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.quiz.repository.QuizRepository.QuizAggregateRow;
import com.ongodmatchu.domain.quiz.repository.QuizStarRepository;
import com.ongodmatchu.domain.user.dto.ProfileStatsResponse;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.global.util.TimeFormat;
import com.ongodmatchu.infra.s3.S3Service;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QuizService {

  private final QuizRepository quizRepository;
  private final QuestionRepository questionRepository;
  private final UserRepository userRepository;
  private final QuizStarRepository quizStarRepository;
  private final QuizAttemptRepository quizAttemptRepository;
  private final QuizCommentRepository quizCommentRepository;
  private final S3Service s3Service;

  public List<CategoryResponse> getCategories() {
    return Arrays.stream(QuizCategory.values()).map(CategoryResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public Page<QuizResponse> getQuizList(String category, Pageable pageable) {
    Page<Quiz> page =
        StringUtils.hasText(category)
            ? quizRepository.findByCategoryAndVisibility(category, QuizVisibility.PUBLIC, pageable)
            : quizRepository.findByVisibility(QuizVisibility.PUBLIC, pageable);

    List<String> keys =
        page.getContent().stream().map(Quiz::getThumbnailKey).filter(k -> k != null).toList();
    Map<String, String> presigned = s3Service.batchPresignViewUrls(keys);

    Map<Long, Double> rateByQuizId = rateMapForPage(page);

    return page.map(
        q ->
            QuizResponse.from(
                q, lookupUrl(presigned, q.getThumbnailKey()), null, rateByQuizId.get(q.getId())));
  }

  private Map<Long, Double> rateMapForPage(Page<Quiz> page) {
    List<Long> quizIds = page.getContent().stream().map(Quiz::getId).toList();
    Map<Long, Double> rateByQuizId = new HashMap<>();
    if (!quizIds.isEmpty()) {
      for (QuizCorrectRateRow r : quizAttemptRepository.correctRateByQuizIds(quizIds)) {
        rateByQuizId.put(r.getQuizId(), r.getRate());
      }
    }
    return rateByQuizId;
  }

  private static String lookupUrl(Map<String, String> presigned, String key) {
    return key == null ? null : presigned.get(key);
  }

  @Transactional(readOnly = true)
  public QuizDetailResponse getQuizDetail(Long quizId, Long viewerUserId) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));

    boolean isOwner = viewerUserId != null && viewerUserId.equals(quiz.getUser().getId());
    if (quiz.getVisibility() == QuizVisibility.PRIVATE && !isOwner) {
      throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND);
    }
    List<Question> questions = questionRepository.findByQuizIdOrderByOrderNum(quizId);

    List<String> keys = new ArrayList<>();
    if (quiz.getThumbnailKey() != null) keys.add(quiz.getThumbnailKey());
    for (Question q : questions) {
      if (q.getImageKey() != null) keys.add(q.getImageKey());
      if (q.getAnswerImageKey() != null) keys.add(q.getAnswerImageKey());
    }
    Map<String, String> presigned = s3Service.batchPresignViewUrls(keys);

    List<QuestionResponse> questionResponses =
        questions.stream()
            .map(
                q ->
                    QuestionResponse.from(
                        q,
                        lookupUrl(presigned, q.getImageKey()),
                        lookupUrl(presigned, q.getAnswerImageKey())))
            .toList();

    Boolean isStarred =
        viewerUserId == null
            ? null
            : quizStarRepository.existsByUserIdAndQuizId(viewerUserId, quiz.getId());
    Double correctRate = singleQuizCorrectRate(quiz.getId());

    return QuizDetailResponse.of(
        quiz,
        lookupUrl(presigned, quiz.getThumbnailKey()),
        isStarred,
        correctRate,
        questionResponses);
  }

  @Transactional
  public QuizResponse createQuiz(Long userId, QuizCreateRequest request) {
    if (!QuizCategory.isValidKey(request.category())) {
      throw new BusinessException(ErrorCode.INVALID_CATEGORY);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    s3Service.verifyKeyOwnedAndCompleted(userId, request.thumbnailKey());
    for (QuestionCreateRequest q : request.questions()) {
      s3Service.verifyKeyOwnedAndCompleted(userId, q.imageKey());
      if (q.answerImageKey() != null && !q.answerImageKey().equals(q.imageKey())) {
        s3Service.verifyKeyOwnedAndCompleted(userId, q.answerImageKey());
      }
    }

    Quiz quiz =
        Quiz.builder()
            .user(user)
            .title(request.title())
            .description(request.description())
            .category(request.category())
            .thumbnailKey(request.thumbnailKey())
            .visibility(request.visibility())
            .build();
    quizRepository.save(quiz);

    for (int i = 0; i < request.questions().size(); i++) {
      QuestionCreateRequest q = request.questions().get(i);
      questionRepository.save(
          Question.builder()
              .quiz(quiz)
              .orderNum(i + 1)
              .imageKey(q.imageKey())
              .answerImageKey(q.answerImageKey())
              .questionText(q.questionText())
              .answer(q.answer())
              .build());
    }

    String thumbnailUrl =
        quiz.getThumbnailKey() == null
            ? null
            : s3Service.generateViewUrl(quiz.getThumbnailKey()).viewUrl();
    return QuizResponse.from(quiz, thumbnailUrl);
  }

  /** 플레이 카운터 증분. PRIVATE 퀴즈는 본인만 호출 가능 (외부에서는 노출 자체가 안 되므로). */
  @Transactional
  public void incrementPlayCount(Long quizId, Long viewerUserId) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
    boolean isOwner = viewerUserId != null && viewerUserId.equals(quiz.getUser().getId());
    if (quiz.getVisibility() == QuizVisibility.PRIVATE && !isOwner) {
      throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND);
    }
    quiz.incrementPlayCount();
  }

  /** 공유 카운터 증분. PRIVATE 퀴즈는 본인만 호출 가능 (외부에서 노출 자체가 안 되므로). */
  @Transactional
  public void incrementShareCount(Long quizId, Long viewerUserId) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
    boolean isOwner = viewerUserId != null && viewerUserId.equals(quiz.getUser().getId());
    if (quiz.getVisibility() == QuizVisibility.PRIVATE && !isOwner) {
      throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND);
    }
    quiz.incrementShareCount();
  }

  private static final int MAX_PAGE_SIZE = 50;
  private static final int DEFAULT_PAGE_SIZE = 20;

  /** 본인의 퀴즈 목록 — visibility 필터 + sort 옵션 + 페이지네이션 cap. */
  @Transactional(readOnly = true)
  public Page<MyQuizListItemResponse> getMyQuizList(
      Long userId, VisibilityFilter visibility, QuizSort sort, Pageable pageable) {
    Pageable effective = applyPageDefaults(pageable, sort);
    Page<Quiz> page =
        switch (visibility) {
          case ALL -> quizRepository.findByUserId(userId, effective);
          case PUBLIC ->
              quizRepository.findByUserIdAndVisibility(userId, QuizVisibility.PUBLIC, effective);
          case PRIVATE ->
              quizRepository.findByUserIdAndVisibility(userId, QuizVisibility.PRIVATE, effective);
        };
    return mapToListItems(page);
  }

  /** 타 유저의 퀴즈 목록. 비공개 프로필 + 외부 뷰어면 빈 페이지. 외부 뷰어는 PUBLIC 퀴즈만, 본인은 전체 노출. 응답 스키마는 본인 목록과 통일. */
  @Transactional(readOnly = true)
  public Page<MyQuizListItemResponse> getQuizListByPublicId(
      UUID publicId, Long viewerUserId, QuizSort sort, Pageable pageable) {
    User author =
        userRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    boolean isOwner = viewerUserId != null && viewerUserId.equals(author.getId());
    Pageable effective = applyPageDefaults(pageable, sort);
    if (!author.isProfilePublic() && !isOwner) {
      return Page.empty(effective);
    }
    Page<Quiz> page =
        isOwner
            ? quizRepository.findByUserId(author.getId(), effective)
            : quizRepository.findByUserIdAndVisibility(
                author.getId(), QuizVisibility.PUBLIC, effective);
    return mapToListItems(page);
  }

  private Page<MyQuizListItemResponse> mapToListItems(Page<Quiz> page) {
    List<String> keys =
        page.getContent().stream().map(Quiz::getThumbnailKey).filter(k -> k != null).toList();
    Map<String, String> presigned = s3Service.batchPresignViewUrls(keys);

    List<Long> quizIds = page.getContent().stream().map(Quiz::getId).toList();
    Map<Long, Double> rateByQuizId = new HashMap<>();
    if (!quizIds.isEmpty()) {
      for (QuizCorrectRateRow r : quizAttemptRepository.correctRateByQuizIds(quizIds)) {
        rateByQuizId.put(r.getQuizId(), r.getRate());
      }
    }

    return page.map(
        q ->
            MyQuizListItemResponse.from(
                q, lookupUrl(presigned, q.getThumbnailKey()), rateByQuizId.get(q.getId())));
  }

  /** 프로필 (내가 만든 퀴즈) 페이지 상단 통계. weeklyPlayCount / avgCorrectRate 는 attempts 기반 집계. */
  @Transactional(readOnly = true)
  public ProfileStatsResponse getProfileStats(Long userId) {
    QuizAggregateRow row = quizRepository.aggregateByUserId(userId);
    LocalDateTime weekStart =
        LocalDate.now(TimeFormat.SERVER_OFFSET)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay();
    long weekly = quizAttemptRepository.countWeeklyPlaysOfQuizzesOwnedBy(userId, weekStart);

    List<Double> perQuizRates = quizAttemptRepository.perQuizCorrectRatesOwnedBy(userId);
    Double avgCorrectRate =
        perQuizRates.isEmpty()
            ? null
            : perQuizRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

    return new ProfileStatsResponse(
        row.getQuizCount(),
        row.getPlays(),
        row.getStars(),
        row.getComments(),
        row.getShares(),
        weekly,
        avgCorrectRate);
  }

  private Pageable applyPageDefaults(Pageable pageable, QuizSort sort) {
    int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
    if (size <= 0) {
      size = DEFAULT_PAGE_SIZE;
    }
    return PageRequest.of(pageable.getPageNumber(), size, sort.toSort());
  }

  @Transactional
  public QuizResponse updateQuiz(Long userId, Long quizId, QuizUpdateRequest request) {
    Quiz quiz = findQuizOwned(userId, quizId);

    if (request.title() != null) {
      quiz.updateTitle(request.title());
    }
    if (request.description() != null) {
      quiz.updateDescription(request.description());
    }
    if (request.category() != null) {
      if (!QuizCategory.isValidKey(request.category())) {
        throw new BusinessException(ErrorCode.INVALID_CATEGORY);
      }
      quiz.updateCategory(request.category());
    }
    if (request.thumbnailKey() != null && !request.thumbnailKey().equals(quiz.getThumbnailKey())) {
      s3Service.verifyKeyOwnedAndCompleted(userId, request.thumbnailKey());
      String previousThumbnailKey = quiz.getThumbnailKey();
      quiz.updateThumbnailKey(request.thumbnailKey());
      if (previousThumbnailKey != null) {
        s3Service.deleteQuietly(previousThumbnailKey);
      }
    }
    if (request.visibility() != null) {
      quiz.changeVisibility(request.visibility());
    }

    String thumbnailUrl =
        quiz.getThumbnailKey() == null
            ? null
            : s3Service.generateViewUrl(quiz.getThumbnailKey()).viewUrl();
    Double correctRate = singleQuizCorrectRate(quiz.getId());
    return QuizResponse.from(quiz, thumbnailUrl, null, correctRate);
  }

  private Double singleQuizCorrectRate(Long quizId) {
    List<QuizCorrectRateRow> rows = quizAttemptRepository.correctRateByQuizIds(List.of(quizId));
    return rows.isEmpty() ? null : rows.get(0).getRate();
  }

  @Transactional
  public void deleteQuiz(Long userId, Long quizId) {
    Quiz quiz = findQuizOwned(userId, quizId);

    List<Question> questions = questionRepository.findByQuizIdOrderByOrderNum(quizId);
    questionRepository.deleteByQuizId(quizId);
    quizRepository.delete(quiz);

    if (quiz.getThumbnailKey() != null) {
      s3Service.deleteQuietly(quiz.getThumbnailKey());
    }
    for (Question q : questions) {
      if (q.getImageKey() != null) s3Service.deleteQuietly(q.getImageKey());
      if (q.getAnswerImageKey() != null && !q.getAnswerImageKey().equals(q.getImageKey())) {
        s3Service.deleteQuietly(q.getAnswerImageKey());
      }
    }
  }

  /**
   * 회원탈퇴 시 본인이 만든 퀴즈와 모든 연관 데이터(질문/시도/스타/댓글)를 일괄 삭제하고 S3 키도 best-effort 정리한다. 단건 삭제와 동일한 의미를 일괄로
   * 처리.
   */
  @Transactional
  public void deleteAllByUserId(Long userId) {
    List<Quiz> quizzes = quizRepository.findAllByUserId(userId);
    if (quizzes.isEmpty()) {
      return;
    }
    List<Long> quizIds = quizzes.stream().map(Quiz::getId).toList();
    List<Question> questions = questionRepository.findByQuizIdIn(quizIds);

    quizCommentRepository.deleteByQuizIdIn(quizIds);
    quizAttemptRepository.deleteByQuizIdIn(quizIds);
    quizStarRepository.deleteByQuizIdIn(quizIds);
    questionRepository.deleteByQuizIdIn(quizIds);
    quizRepository.deleteAllInBatch(quizzes);

    for (Quiz q : quizzes) {
      if (q.getThumbnailKey() != null) s3Service.deleteQuietly(q.getThumbnailKey());
    }
    for (Question q : questions) {
      if (q.getImageKey() != null) s3Service.deleteQuietly(q.getImageKey());
      if (q.getAnswerImageKey() != null && !q.getAnswerImageKey().equals(q.getImageKey())) {
        s3Service.deleteQuietly(q.getAnswerImageKey());
      }
    }
  }

  /** 회원탈퇴 시 본인 퀴즈 작성자를 시스템 관리자 계정으로 일괄 이전. 변경된 행 수를 반환한다. */
  @Transactional
  public int transferOwnershipToAdmin(Long fromUserId, Long adminUserId) {
    return quizRepository.transferOwnership(fromUserId, adminUserId);
  }

  private Quiz findQuizOwned(Long userId, Long quizId) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
    if (!quiz.getUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.QUIZ_FORBIDDEN);
    }
    return quiz;
  }
}
