package com.ongodmatchu.domain.quiz.repository;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

  Page<Quiz> findByCategoryAndVisibility(
      String category, QuizVisibility visibility, Pageable pageable);

  Page<Quiz> findByVisibility(QuizVisibility visibility, Pageable pageable);

  Page<Quiz> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

  Page<Quiz> findByUserIdAndVisibilityOrderByCreatedAtDesc(
      Long userId, QuizVisibility visibility, Pageable pageable);
}
