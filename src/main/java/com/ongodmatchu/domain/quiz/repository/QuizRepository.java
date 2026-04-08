package com.ongodmatchu.domain.quiz.repository;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

  Page<Quiz> findByCategory(String category, Pageable pageable);
}
