package com.ongodmatchu.domain.quiz.service;

import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.dto.CategoryResponse;
import com.ongodmatchu.domain.quiz.dto.QuestionCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuestionResponse;
import com.ongodmatchu.domain.quiz.dto.QuizCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuizDetailResponse;
import com.ongodmatchu.domain.quiz.dto.QuizResponse;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizCategory;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.s3.S3Service;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
  private final S3Service s3Service;

  public List<CategoryResponse> getCategories() {
    return Arrays.stream(QuizCategory.values()).map(CategoryResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public Page<QuizResponse> getQuizList(String category, Pageable pageable) {
    Page<Quiz> page =
        StringUtils.hasText(category)
            ? quizRepository.findByCategory(category, pageable)
            : quizRepository.findAll(pageable);

    List<String> keys =
        page.getContent().stream().map(Quiz::getThumbnailKey).filter(k -> k != null).toList();
    Map<String, String> presigned = s3Service.batchPresignViewUrls(keys);

    return page.map(q -> QuizResponse.from(q, lookupUrl(presigned, q.getThumbnailKey())));
  }

  private static String lookupUrl(Map<String, String> presigned, String key) {
    return key == null ? null : presigned.get(key);
  }

  @Transactional(readOnly = true)
  public QuizDetailResponse getQuizDetail(Long quizId) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
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

    return QuizDetailResponse.of(
        quiz, lookupUrl(presigned, quiz.getThumbnailKey()), questionResponses);
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
}
