package com.ongodmatchu.domain.quiz.repository;

import com.ongodmatchu.domain.quiz.entity.QuizStar;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface QuizStarRepository extends JpaRepository<QuizStar, Long> {

  boolean existsByUserIdAndQuizId(Long userId, Long quizId);

  long deleteByUserIdAndQuizId(Long userId, Long quizId);

  @Transactional
  void deleteByQuizIdIn(Collection<Long> quizIds);
}
