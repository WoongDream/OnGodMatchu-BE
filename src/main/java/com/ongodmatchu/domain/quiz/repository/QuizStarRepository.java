package com.ongodmatchu.domain.quiz.repository;

import com.ongodmatchu.domain.quiz.entity.QuizStar;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface QuizStarRepository extends JpaRepository<QuizStar, Long> {

  boolean existsByUserIdAndQuizId(Long userId, Long quizId);

  long deleteByUserIdAndQuizId(Long userId, Long quizId);

  @Transactional
  void deleteByQuizIdIn(Collection<Long> quizIds);

  @Query("SELECT s.quiz.id FROM QuizStar s WHERE s.user.id = :userId AND s.quiz.id IN :quizIds")
  List<Long> findStarredQuizIds(
      @Param("userId") Long userId, @Param("quizIds") Collection<Long> quizIds);
}
