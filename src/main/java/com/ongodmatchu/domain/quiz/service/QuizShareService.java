package com.ongodmatchu.domain.quiz.service;

import com.ongodmatchu.domain.quiz.dto.QuizShareResponse;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizShare;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.quiz.repository.QuizShareRepository;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 퀴즈 공유 카운트 — 사용자(user_id) 또는 익명(anon_id) 단위로 1회만 증가. partial unique 인덱스 (uq_quiz_share_user /
 * uq_quiz_share_anon) 가 중복을 DB 레벨에서 가드.
 */
@Service
@RequiredArgsConstructor
public class QuizShareService {

  private final QuizRepository quizRepository;
  private final QuizShareRepository quizShareRepository;
  private final UserRepository userRepository;

  @Transactional
  public QuizShareResponse recordShare(Long quizId, Long userId, String anonId) {
    if (userId == null && (anonId == null || anonId.isBlank())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
    Quiz quiz = findVisibleQuiz(quizId, userId);

    boolean already =
        userId != null
            ? quizShareRepository.existsByQuizIdAndUserId(quizId, userId)
            : quizShareRepository.existsByQuizIdAndAnonId(quizId, anonId);
    if (already) {
      return new QuizShareResponse(quiz.getShareCount(), true);
    }

    User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
    try {
      quizShareRepository.saveAndFlush(
          QuizShare.builder().quiz(quiz).user(user).anonId(userId == null ? anonId : null).build());
    } catch (DataIntegrityViolationException ex) {
      // 동시 클릭 race — partial unique 가 한쪽 reject. 카운트 증가 스킵.
      return new QuizShareResponse(quiz.getShareCount(), true);
    }
    quiz.incrementShareCount();
    return new QuizShareResponse(quiz.getShareCount(), false);
  }

  private Quiz findVisibleQuiz(Long quizId, Long viewerUserId) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
    boolean isOwner = viewerUserId != null && viewerUserId.equals(quiz.getUser().getId());
    if (quiz.getVisibility() == QuizVisibility.PRIVATE && !isOwner) {
      throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND);
    }
    return quiz;
  }
}
