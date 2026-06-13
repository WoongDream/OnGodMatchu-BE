package com.ongodmatchu.domain.quiz.service;

import com.ongodmatchu.domain.comment.repository.QuizCommentRepository;
import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.dto.CategoryResponse;
import com.ongodmatchu.domain.quiz.dto.MyQuizListItemResponse;
import com.ongodmatchu.domain.quiz.dto.PublicProfileStats;
import com.ongodmatchu.domain.quiz.dto.QuestionCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuestionResponse;
import com.ongodmatchu.domain.quiz.dto.QuestionUpdateRequest;
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
import com.ongodmatchu.global.util.ImageTransformPolicy;
import com.ongodmatchu.global.util.TimeFormat;
import com.ongodmatchu.infra.s3.S3Service;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  @Transactional(readOnly = true)
  public List<CategoryResponse> getCategories() {
    Map<String, Long> playCounts =
        quizRepository.sumPlayCountByCategory(QuizVisibility.PUBLIC).stream()
            .collect(
                Collectors.toMap(
                    QuizRepository.CategoryPlayCountRow::getCategory,
                    QuizRepository.CategoryPlayCountRow::getPlays));
    // 총 플레이수 내림차순. 동률(플레이 0 포함)은 stable sort 라 enum 선언 순서 유지.
    return Arrays.stream(QuizCategory.values())
        .sorted(
            Comparator.comparingLong((QuizCategory c) -> playCounts.getOrDefault(c.getKey(), 0L))
                .reversed())
        .map(CategoryResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<QuizResponse> getQuizList(
      String category, String keyword, Long viewerUserId, Pageable pageable) {
    Page<Quiz> page =
        quizRepository.searchPublic(
            QuizVisibility.PUBLIC, normalizeTitle(category), normalizeTitle(keyword), pageable);

    List<String> keys =
        page.getContent().stream().map(Quiz::getThumbnailKey).filter(k -> k != null).toList();
    Map<String, String> presigned = s3Service.batchPresignViewUrls(keys);

    Map<Long, Double> rateByQuizId = rateMapForPage(page);
    Set<Long> starredQuizIds = starredQuizIdsForPage(viewerUserId, page);

    return page.map(
        q ->
            QuizResponse.from(
                q,
                lookupUrl(presigned, q.getThumbnailKey()),
                viewerUserId == null ? null : starredQuizIds.contains(q.getId()),
                rateByQuizId.get(q.getId())));
  }

  /**
   * 내가 스타 준 퀴즈 목록 (내 전용). 스타 누른 시각 DESC, 메인 목록과 동일한 QuizResponse 스키마. isStarred 는 전부 true. title 로
   * 제목 부분일치 검색(옵션). size 디폴트 20 / 최대 50.
   */
  @Transactional(readOnly = true)
  public Page<QuizResponse> getMyStarredQuizzes(Long userId, String title, Pageable pageable) {
    Pageable effective = applyPageDefaultsNoSort(pageable);
    Page<Quiz> page =
        quizStarRepository.findStarredQuizzesByUserId(userId, normalizeTitle(title), effective);

    List<String> keys =
        page.getContent().stream().map(Quiz::getThumbnailKey).filter(k -> k != null).toList();
    Map<String, String> presigned = s3Service.batchPresignViewUrls(keys);
    Map<Long, Double> rateByQuizId = rateMapForPage(page);

    return page.map(
        q ->
            QuizResponse.from(
                q, lookupUrl(presigned, q.getThumbnailKey()), true, rateByQuizId.get(q.getId())));
  }

  private Pageable applyPageDefaultsNoSort(Pageable pageable) {
    int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
    if (size <= 0) {
      size = DEFAULT_PAGE_SIZE;
    }
    return PageRequest.of(pageable.getPageNumber(), size);
  }

  private static String normalizeTitle(String title) {
    return (title == null || title.isBlank()) ? null : title.trim();
  }

  private Set<Long> starredQuizIdsForPage(Long viewerUserId, Page<Quiz> page) {
    if (viewerUserId == null || page.isEmpty()) {
      return Collections.emptySet();
    }
    List<Long> quizIds = page.getContent().stream().map(Quiz::getId).toList();
    return new HashSet<>(quizStarRepository.findStarredQuizIds(viewerUserId, quizIds));
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

  /** 원본 key 검증 — 크롭 결과 key 와 같으면(크롭 안 함) 이미 검증됐으므로 skip. */
  private void verifyOriginalKey(Long userId, String originalKey, String croppedKey) {
    if (originalKey != null && !originalKey.equals(croppedKey)) {
      s3Service.verifyKeyOwnedAndCompleted(userId, originalKey);
    }
  }

  /** key 가 keptKeys 중 어느 것과도 같지 않으면 best-effort 삭제 (재사용 중인 key 는 보존). */
  private void deleteIfUnused(String key, String... keptKeys) {
    if (key == null) {
      return;
    }
    for (String kept : keptKeys) {
      if (key.equals(kept)) {
        return;
      }
    }
    s3Service.deleteQuietly(key);
  }

  private static void addIfPresent(Set<String> dest, String key) {
    if (key != null) {
      dest.add(key);
    }
  }

  private static void addToDeleteIfUnused(Set<String> dest, String key, Set<String> usedKeysAfter) {
    if (key != null && !usedKeysAfter.contains(key)) {
      dest.add(key);
    }
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
    if (quiz.getOriginalThumbnailKey() != null) keys.add(quiz.getOriginalThumbnailKey());
    if (quiz.getUser().getProfileImageKey() != null) keys.add(quiz.getUser().getProfileImageKey());
    for (Question q : questions) {
      if (q.getImageKey() != null) keys.add(q.getImageKey());
      if (q.getOriginalImageKey() != null) keys.add(q.getOriginalImageKey());
      if (q.getAnswerImageKey() != null) keys.add(q.getAnswerImageKey());
      if (q.getOriginalAnswerImageKey() != null) keys.add(q.getOriginalAnswerImageKey());
    }
    Map<String, String> presigned = s3Service.batchPresignViewUrls(keys);

    List<QuestionResponse> questionResponses =
        questions.stream()
            .map(
                q ->
                    QuestionResponse.from(
                        q,
                        lookupUrl(presigned, q.getImageKey()),
                        lookupUrl(presigned, q.getOriginalImageKey()),
                        lookupUrl(presigned, q.getAnswerImageKey()),
                        lookupUrl(presigned, q.getOriginalAnswerImageKey()),
                        isOwner))
            .toList();

    Boolean isStarred =
        viewerUserId == null
            ? null
            : quizStarRepository.existsByUserIdAndQuizId(viewerUserId, quiz.getId());
    Double correctRate = singleQuizCorrectRate(quiz.getId());

    return QuizDetailResponse.of(
        quiz,
        lookupUrl(presigned, quiz.getThumbnailKey()),
        lookupUrl(presigned, quiz.getOriginalThumbnailKey()),
        isOwner,
        isStarred,
        correctRate,
        lookupUrl(presigned, quiz.getUser().getProfileImageKey()),
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
    verifyOriginalKey(userId, request.originalThumbnailKey(), request.thumbnailKey());
    for (QuestionCreateRequest q : request.questions()) {
      s3Service.verifyKeyOwnedAndCompleted(userId, q.imageKey());
      verifyOriginalKey(userId, q.originalImageKey(), q.imageKey());
      if (q.answerImageKey() != null && !q.answerImageKey().equals(q.imageKey())) {
        s3Service.verifyKeyOwnedAndCompleted(userId, q.answerImageKey());
      }
      verifyOriginalKey(userId, q.originalAnswerImageKey(), q.answerImageKey());
    }

    Quiz quiz =
        Quiz.builder()
            .user(user)
            .title(request.title())
            .description(request.description())
            .category(request.category())
            .thumbnailKey(request.thumbnailKey())
            .originalThumbnailKey(request.originalThumbnailKey())
            .thumbnailTransform(ImageTransformPolicy.toStoredJson(request.thumbnailTransform()))
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
              .originalImageKey(q.originalImageKey())
              .imageTransform(ImageTransformPolicy.toStoredJson(q.imageTransform()))
              .answerImageKey(q.answerImageKey())
              .originalAnswerImageKey(q.originalAnswerImageKey())
              .answerImageTransform(ImageTransformPolicy.toStoredJson(q.answerImageTransform()))
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

    long solvedCount = quizAttemptRepository.countByUserId(userId);
    Double avgSolveRate = quizAttemptRepository.avgSolveRateOf(userId);

    return new ProfileStatsResponse(
        row.getQuizCount(),
        row.getPlays(),
        row.getStars(),
        row.getComments(),
        row.getShares(),
        weekly,
        avgCorrectRate,
        solvedCount,
        avgSolveRate);
  }

  /**
   * 타인 시점 프로필 요약 통계 — 전부 PUBLIC 퀴즈 기준 (비공개 퀴즈는 타인 노출/통계 제외). 만든 퀴즈(개수/총플레이/받은스타)는 PUBLIC 집계,
   * 풀어봄/정답률은 PUBLIC 퀴즈 attempt 한정.
   */
  @Transactional(readOnly = true)
  public PublicProfileStats getPublicProfileStats(Long userId) {
    QuizAggregateRow row =
        quizRepository.aggregateByUserIdAndVisibility(userId, QuizVisibility.PUBLIC);
    long solvedCount =
        quizAttemptRepository.countByUserIdAndQuizVisibility(userId, QuizVisibility.PUBLIC);
    Double avgSolveRate =
        quizAttemptRepository.avgSolveRateOfByQuizVisibility(userId, QuizVisibility.PUBLIC);
    return new PublicProfileStats(
        row.getQuizCount(), row.getPlays(), row.getStars(), solvedCount, avgSolveRate);
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
      verifyOriginalKey(userId, request.originalThumbnailKey(), request.thumbnailKey());
      String prevThumb = quiz.getThumbnailKey();
      String prevOriginal = quiz.getOriginalThumbnailKey();
      quiz.updateThumbnail(
          request.thumbnailKey(),
          request.originalThumbnailKey(),
          ImageTransformPolicy.toStoredJson(request.thumbnailTransform()));
      deleteIfUnused(prevThumb, request.thumbnailKey(), request.originalThumbnailKey());
      deleteIfUnused(prevOriginal, request.thumbnailKey(), request.originalThumbnailKey());
    }
    if (request.visibility() != null) {
      quiz.changeVisibility(request.visibility());
    }
    if (request.questions() != null) {
      applyQuestionsDiff(userId, quiz, request.questions());
    }

    String thumbnailUrl =
        quiz.getThumbnailKey() == null
            ? null
            : s3Service.generateViewUrl(quiz.getThumbnailKey()).viewUrl();
    Double correctRate = singleQuizCorrectRate(quiz.getId());
    return QuizResponse.from(quiz, thumbnailUrl, null, correctRate);
  }

  /**
   * id 유지 PUT 의미론으로 questions diff 처리. payload 의 각 항목 id 가 있으면 기존 갱신, 없으면 신규 추가. payload 에서 빠진 기존
   * id 는 삭제. orderNum 은 payload 순서대로 1부터 재할당. 더 이상 사용하지 않는 이미지 key 는 S3 best-effort 삭제. quiz 자체 필드가
   * 변경되지 않아도 quiz.touch() 로 updatedAt 강제 갱신.
   */
  private void applyQuestionsDiff(Long userId, Quiz quiz, List<QuestionUpdateRequest> payload) {
    List<Question> existing = questionRepository.findByQuizIdOrderByOrderNum(quiz.getId());
    Map<Long, Question> existingById =
        existing.stream().collect(Collectors.toMap(Question::getId, q -> q));

    for (QuestionUpdateRequest req : payload) {
      if (req.id() != null && !existingById.containsKey(req.id())) {
        throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
      }
    }

    Set<String> usedKeysAfter = new HashSet<>();
    for (QuestionUpdateRequest req : payload) {
      Question prev = req.id() != null ? existingById.get(req.id()) : null;
      String prevImageKey = prev != null ? prev.getImageKey() : null;
      String prevAnswerImageKey = prev != null ? prev.getAnswerImageKey() : null;
      String prevOriginalImageKey = prev != null ? prev.getOriginalImageKey() : null;
      String prevOriginalAnswerImageKey = prev != null ? prev.getOriginalAnswerImageKey() : null;

      if (req.imageKey() != null && !req.imageKey().equals(prevImageKey)) {
        s3Service.verifyKeyOwnedAndCompleted(userId, req.imageKey());
      }
      if (req.originalImageKey() != null
          && !req.originalImageKey().equals(prevOriginalImageKey)
          && !req.originalImageKey().equals(req.imageKey())) {
        s3Service.verifyKeyOwnedAndCompleted(userId, req.originalImageKey());
      }
      if (req.answerImageKey() != null
          && !req.answerImageKey().equals(prevAnswerImageKey)
          && !req.answerImageKey().equals(req.imageKey())) {
        s3Service.verifyKeyOwnedAndCompleted(userId, req.answerImageKey());
      }
      if (req.originalAnswerImageKey() != null
          && !req.originalAnswerImageKey().equals(prevOriginalAnswerImageKey)
          && !req.originalAnswerImageKey().equals(req.answerImageKey())) {
        s3Service.verifyKeyOwnedAndCompleted(userId, req.originalAnswerImageKey());
      }

      addIfPresent(usedKeysAfter, req.imageKey());
      addIfPresent(usedKeysAfter, req.originalImageKey());
      addIfPresent(usedKeysAfter, req.answerImageKey());
      addIfPresent(usedKeysAfter, req.originalAnswerImageKey());
    }

    Set<Long> keepIds =
        payload.stream()
            .map(QuestionUpdateRequest::id)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

    // 기존 질문의 모든 key(크롭+원본)를 훑어 더 이상 쓰이지 않는 것만 삭제 대상에 모은다.
    // 삭제된 질문의 key, 갱신으로 교체돼 떨어져 나간 옛 key 모두 usedKeysAfter 에 없으면 orphan.
    Set<String> keysToDelete = new HashSet<>();
    List<Question> toDelete = new ArrayList<>();
    for (Question q : existing) {
      if (!keepIds.contains(q.getId())) {
        toDelete.add(q);
      }
      addToDeleteIfUnused(keysToDelete, q.getImageKey(), usedKeysAfter);
      addToDeleteIfUnused(keysToDelete, q.getOriginalImageKey(), usedKeysAfter);
      addToDeleteIfUnused(keysToDelete, q.getAnswerImageKey(), usedKeysAfter);
      addToDeleteIfUnused(keysToDelete, q.getOriginalAnswerImageKey(), usedKeysAfter);
    }

    if (!toDelete.isEmpty()) {
      questionRepository.deleteAll(toDelete);
    }

    for (int i = 0; i < payload.size(); i++) {
      QuestionUpdateRequest req = payload.get(i);
      int orderNum = i + 1;
      if (req.id() != null) {
        Question prev = existingById.get(req.id());
        prev.update(
            orderNum,
            req.questionText(),
            req.answer(),
            req.imageKey(),
            req.originalImageKey(),
            ImageTransformPolicy.toStoredJson(req.imageTransform()),
            req.answerImageKey(),
            req.originalAnswerImageKey(),
            ImageTransformPolicy.toStoredJson(req.answerImageTransform()));
      } else {
        questionRepository.save(
            Question.builder()
                .quiz(quiz)
                .orderNum(orderNum)
                .imageKey(req.imageKey())
                .originalImageKey(req.originalImageKey())
                .imageTransform(ImageTransformPolicy.toStoredJson(req.imageTransform()))
                .answerImageKey(req.answerImageKey())
                .originalAnswerImageKey(req.originalAnswerImageKey())
                .answerImageTransform(ImageTransformPolicy.toStoredJson(req.answerImageTransform()))
                .questionText(req.questionText())
                .answer(req.answer())
                .build());
      }
    }

    quiz.touch();

    for (String key : keysToDelete) {
      s3Service.deleteQuietly(key);
    }
  }

  private Double singleQuizCorrectRate(Long quizId) {
    List<QuizCorrectRateRow> rows = quizAttemptRepository.correctRateByQuizIds(List.of(quizId));
    return rows.isEmpty() ? null : rows.get(0).getRate();
  }

  @Transactional
  public void deleteQuiz(Long userId, Long quizId) {
    Quiz quiz = findQuizOwned(userId, quizId);

    List<Question> questions = questionRepository.findByQuizIdOrderByOrderNum(quizId);
    List<Long> quizIds = List.of(quizId);
    quizCommentRepository.deleteByQuizIdIn(quizIds);
    quizAttemptRepository.deleteByQuizIdIn(quizIds);
    quizStarRepository.deleteByQuizIdIn(quizIds);
    questionRepository.deleteByQuizId(quizId);
    quizRepository.delete(quiz);

    Set<String> keys = new HashSet<>();
    addIfPresent(keys, quiz.getThumbnailKey());
    addIfPresent(keys, quiz.getOriginalThumbnailKey());
    for (Question q : questions) {
      collectQuestionKeys(keys, q);
    }
    for (String key : keys) {
      s3Service.deleteQuietly(key);
    }
  }

  private static void collectQuestionKeys(Set<String> dest, Question q) {
    addIfPresent(dest, q.getImageKey());
    addIfPresent(dest, q.getOriginalImageKey());
    addIfPresent(dest, q.getAnswerImageKey());
    addIfPresent(dest, q.getOriginalAnswerImageKey());
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

    Set<String> keys = new HashSet<>();
    for (Quiz q : quizzes) {
      addIfPresent(keys, q.getThumbnailKey());
      addIfPresent(keys, q.getOriginalThumbnailKey());
    }
    for (Question q : questions) {
      collectQuestionKeys(keys, q);
    }
    for (String key : keys) {
      s3Service.deleteQuietly(key);
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
