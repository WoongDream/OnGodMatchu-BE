package com.ongodmatchu.domain.quiz.repository;

import com.ongodmatchu.domain.quiz.entity.QuizShare;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizShareRepository extends JpaRepository<QuizShare, Long> {

  boolean existsByQuizIdAndUserId(Long quizId, Long userId);

  boolean existsByQuizIdAndAnonId(Long quizId, String anonId);
}
