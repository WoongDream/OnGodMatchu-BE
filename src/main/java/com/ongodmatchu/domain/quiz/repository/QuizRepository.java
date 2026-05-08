package com.ongodmatchu.domain.quiz.repository;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

  Page<Quiz> findByCategoryAndVisibility(
      String category, QuizVisibility visibility, Pageable pageable);

  Page<Quiz> findByVisibility(QuizVisibility visibility, Pageable pageable);

  Page<Quiz> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

  Page<Quiz> findByUserId(Long userId, Pageable pageable);

  Page<Quiz> findByUserIdAndVisibility(Long userId, QuizVisibility visibility, Pageable pageable);

  Page<Quiz> findByUserIdAndVisibilityOrderByCreatedAtDesc(
      Long userId, QuizVisibility visibility, Pageable pageable);

  @Query(
      "SELECT COUNT(q) AS quizCount, "
          + "COALESCE(SUM(q.playCount), 0) AS plays, "
          + "COALESCE(SUM(q.starCount), 0) AS stars, "
          + "COALESCE(SUM(q.commentCount), 0) AS comments, "
          + "COALESCE(SUM(q.shareCount), 0) AS shares "
          + "FROM Quiz q WHERE q.user.id = :userId")
  QuizAggregateRow aggregateByUserId(@Param("userId") Long userId);

  interface QuizAggregateRow {
    long getQuizCount();

    long getPlays();

    long getStars();

    long getComments();

    long getShares();
  }
}
