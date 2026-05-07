package com.ongodmatchu.domain.quiz.service;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizStar;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.quiz.repository.QuizStarRepository;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuizStarService {

  private final QuizRepository quizRepository;
  private final QuizStarRepository quizStarRepository;
  private final UserRepository userRepository;

  /** 좋아요 누름 — 멱등. PRIVATE 퀴즈는 본인만. */
  @Transactional
  public void star(Long userId, Long quizId) {
    Quiz quiz = findVisibleQuiz(userId, quizId);
    if (quizStarRepository.existsByUserIdAndQuizId(userId, quizId)) {
      return;
    }
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    quizStarRepository.save(QuizStar.builder().user(user).quiz(quiz).build());
    quiz.incrementStarCount();
  }

  /** 좋아요 취소 — 멱등. */
  @Transactional
  public void unstar(Long userId, Long quizId) {
    Quiz quiz = findVisibleQuiz(userId, quizId);
    long deleted = quizStarRepository.deleteByUserIdAndQuizId(userId, quizId);
    if (deleted > 0) {
      quiz.decrementStarCount();
    }
  }

  private Quiz findVisibleQuiz(Long userId, Long quizId) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
    boolean isOwner = quiz.getUser().getId().equals(userId);
    if (quiz.getVisibility() == QuizVisibility.PRIVATE && !isOwner) {
      throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND);
    }
    return quiz;
  }
}
