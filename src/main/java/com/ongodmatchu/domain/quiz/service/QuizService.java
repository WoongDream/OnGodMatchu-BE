package com.ongodmatchu.domain.quiz.service;

import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.dto.CategoryResponse;
import com.ongodmatchu.domain.quiz.dto.QuestionCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuestionResponse;
import com.ongodmatchu.domain.quiz.dto.QuizCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuizDetailResponse;
import com.ongodmatchu.domain.quiz.dto.QuizResponse;
import com.ongodmatchu.domain.quiz.dto.QuizUpdateRequest;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizCategory;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.quiz.repository.QuizStarRepository;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.s3.S3Service;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    return page.map(q -> QuizResponse.from(q, lookupUrl(presigned, q.getThumbnailKey())));
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

    boolean isStarred =
        viewerUserId != null
            && quizStarRepository.existsByUserIdAndQuizId(viewerUserId, quiz.getId());
    return QuizDetailResponse.of(
        quiz, lookupUrl(presigned, quiz.getThumbnailKey()), isStarred, questionResponses);
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

  @Transactional
  public void incrementPlayCount(Long quizId) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
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

  /** 본인의 퀴즈 목록. */
  @Transactional(readOnly = true)
  public Page<QuizResponse> getMyQuizList(Long userId, Pageable pageable) {
    Page<Quiz> page = quizRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    return mapWithThumbnails(page);
  }

  /** 타 유저의 퀴즈 목록. 비공개 프로필 + 외부 뷰어면 빈 페이지. 외부 뷰어는 PUBLIC 퀴즈만, 본인은 전체 노출. */
  @Transactional(readOnly = true)
  public Page<QuizResponse> getQuizListByPublicId(
      UUID publicId, Long viewerUserId, Pageable pageable) {
    User author =
        userRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    boolean isOwner = viewerUserId != null && viewerUserId.equals(author.getId());
    if (!author.isProfilePublic() && !isOwner) {
      return Page.empty(pageable);
    }
    Page<Quiz> page =
        isOwner
            ? quizRepository.findByUserIdOrderByCreatedAtDesc(author.getId(), pageable)
            : quizRepository.findByUserIdAndVisibilityOrderByCreatedAtDesc(
                author.getId(), QuizVisibility.PUBLIC, pageable);
    return mapWithThumbnails(page);
  }

  private Page<QuizResponse> mapWithThumbnails(Page<Quiz> page) {
    List<String> keys =
        page.getContent().stream().map(Quiz::getThumbnailKey).filter(k -> k != null).toList();
    Map<String, String> presigned = s3Service.batchPresignViewUrls(keys);
    return page.map(q -> QuizResponse.from(q, lookupUrl(presigned, q.getThumbnailKey())));
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
    return QuizResponse.from(quiz, thumbnailUrl);
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
