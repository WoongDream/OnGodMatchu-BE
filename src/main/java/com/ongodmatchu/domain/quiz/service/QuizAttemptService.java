package com.ongodmatchu.domain.quiz.service;

import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.dto.AttemptAnswerRequest;
import com.ongodmatchu.domain.quiz.dto.AttemptCreateRequest;
import com.ongodmatchu.domain.quiz.dto.AttemptItemResultResponse;
import com.ongodmatchu.domain.quiz.dto.AttemptListItemResponse;
import com.ongodmatchu.domain.quiz.dto.AttemptResultResponse;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizAttempt;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.quiz.repository.QuizAttemptRepository;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.ai.AiGradingService;
import com.ongodmatchu.infra.s3.S3Service;
import java.util.ArrayList;
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

@Service
@RequiredArgsConstructor
public class QuizAttemptService {

  private static final int MAX_PAGE_SIZE = 50;
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final QuizRepository quizRepository;
  private final QuestionRepository questionRepository;
  private final QuizAttemptRepository quizAttemptRepository;
  private final UserRepository userRepository;
  private final AiGradingService aiGradingService;
  private final S3Service s3Service;

  /**
   * 풀이 1회 제출 (A안 — 서버 일괄 채점). PRIVATE 퀴즈 + 외부 뷰어면 QUIZ_NOT_FOUND. Quiz.playCount 는 비로그인 포함 항상 증분,
   * attempt 저장은 로그인 시에만.
   */
  @Transactional
  public AttemptResultResponse submit(
      Long quizId, Long viewerUserId, AttemptCreateRequest request) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));

    boolean isOwner = viewerUserId != null && viewerUserId.equals(quiz.getUser().getId());
    if (quiz.getVisibility() == QuizVisibility.PRIVATE && !isOwner) {
      throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND);
    }

    List<Question> questions = questionRepository.findByQuizIdOrderByOrderNum(quizId);
    Map<Long, Question> byId = new HashMap<>();
    for (Question q : questions) {
      byId.put(q.getId(), q);
    }

    List<AttemptItemResultResponse> results = new ArrayList<>(request.answers().size());
    int score = 0;
    for (AttemptAnswerRequest a : request.answers()) {
      Question q = byId.get(a.questionId());
      if (q == null) {
        throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
      }
      boolean correct =
          isExactMatch(q.getAnswer(), a.userAnswer())
              || aiGradingService.grade(q.getAnswer(), a.userAnswer());
      if (correct) {
        score++;
      }
      results.add(new AttemptItemResultResponse(q.getId(), correct, q.getAnswer(), a.userAnswer()));
    }

    int totalQuestions = questions.size();
    quiz.incrementPlayCount();

    Long attemptId = null;
    if (viewerUserId != null) {
      User user =
          userRepository
              .findById(viewerUserId)
              .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
      QuizAttempt saved =
          quizAttemptRepository.save(
              QuizAttempt.builder()
                  .user(user)
                  .quiz(quiz)
                  .score(score)
                  .totalQuestions(totalQuestions)
                  .build());
      attemptId = saved.getId();
    }

    Double percent = totalQuestions > 0 ? (score * 100.0 / totalQuestions) : null;
    return new AttemptResultResponse(attemptId, score, totalQuestions, percent, results);
  }

  /** 내 풀이 기록 목록. completedAt DESC, size 디폴트 20 / 최대 50. */
  @Transactional(readOnly = true)
  public Page<AttemptListItemResponse> getMyAttempts(Long userId, Pageable pageable) {
    Pageable effective = applyPageDefaults(pageable);
    Page<QuizAttempt> page =
        quizAttemptRepository.findByUserIdOrderByCompletedAtDesc(userId, effective);
    return mapToListItems(page);
  }

  /** 외부 뷰어 + 비공개 프로필 → 빈 페이지. 본인이거나 공개 프로필이면 정상 반환. */
  @Transactional(readOnly = true)
  public Page<AttemptListItemResponse> getAttemptsByPublicId(
      UUID publicId, Long viewerUserId, Pageable pageable) {
    User author =
        userRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    Pageable effective = applyPageDefaults(pageable);
    boolean isOwner = viewerUserId != null && viewerUserId.equals(author.getId());
    if (!author.isProfilePublic() && !isOwner) {
      return Page.empty(effective);
    }
    Page<QuizAttempt> page =
        quizAttemptRepository.findByUserIdOrderByCompletedAtDesc(author.getId(), effective);
    return mapToListItems(page);
  }

  private Page<AttemptListItemResponse> mapToListItems(Page<QuizAttempt> page) {
    List<String> keys =
        page.getContent().stream()
            .map(a -> a.getQuiz().getThumbnailKey())
            .filter(k -> k != null)
            .toList();
    Map<String, String> presigned = s3Service.batchPresignViewUrls(keys);
    return page.map(
        a -> AttemptListItemResponse.from(a, lookupUrl(presigned, a.getQuiz().getThumbnailKey())));
  }

  private static String lookupUrl(Map<String, String> presigned, String key) {
    return key == null ? null : presigned.get(key);
  }

  private boolean isExactMatch(String correctAnswer, String userAnswer) {
    return correctAnswer.trim().equalsIgnoreCase(userAnswer.trim());
  }

  private Pageable applyPageDefaults(Pageable pageable) {
    int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
    if (size <= 0) {
      size = DEFAULT_PAGE_SIZE;
    }
    return PageRequest.of(pageable.getPageNumber(), size);
  }
}
