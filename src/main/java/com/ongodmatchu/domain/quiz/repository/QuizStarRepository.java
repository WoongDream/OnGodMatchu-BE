package com.ongodmatchu.domain.quiz.repository;

import com.ongodmatchu.domain.quiz.entity.QuizStar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizStarRepository extends JpaRepository<QuizStar, Long> {

  boolean existsByUserIdAndQuizId(Long userId, Long quizId);

  long deleteByUserIdAndQuizId(Long userId, Long quizId);
}
