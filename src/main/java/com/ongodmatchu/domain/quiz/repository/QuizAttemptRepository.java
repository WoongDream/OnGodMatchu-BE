package com.ongodmatchu.domain.quiz.repository;

import com.ongodmatchu.domain.quiz.entity.QuizAttempt;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

  Page<QuizAttempt> findByUserIdOrderByCompletedAtDesc(Long userId, Pageable pageable);

  /**
   * 본인 소유 퀴즈에 대한 attempts 중 KST 이번주(월요일 00:00 ~ now) 시도 수. 호출자는 KST 월요일 0시 시각을 LocalDateTime 으로 직접
   * 계산해 넘긴다 (시간대 일관성은 호출자 책임).
   */
  @Query(
      "SELECT COUNT(a) FROM QuizAttempt a "
          + "WHERE a.quiz.user.id = :userId AND a.completedAt >= :weekStart")
  long countWeeklyPlaysOfQuizzesOwnedBy(
      @Param("userId") Long userId, @Param("weekStart") LocalDateTime weekStart);

  /**
   * 본인이 소유한 퀴즈 중 시도 1회 이상 있는 각 퀴즈의 정답률 (0~100) 목록. avgCorrectRate 는 Service 단에서 이 목록의 단순 평균으로 산출 —
   * JPQL FROM 절 서브쿼리 미지원 회피.
   */
  @Query(
      "SELECT (SUM(a.score) * 100.0 / SUM(a.totalQuestions)) "
          + "FROM QuizAttempt a WHERE a.quiz.user.id = :userId GROUP BY a.quiz.id")
  List<Double> perQuizCorrectRatesOwnedBy(@Param("userId") Long userId);

  /** 주어진 퀴즈 ID 들의 정답률(0~100). 시도 없는 퀴즈는 결과에 누락. */
  @Query(
      "SELECT a.quiz.id AS quizId, "
          + "(SUM(a.score) * 100.0 / SUM(a.totalQuestions)) AS rate "
          + "FROM QuizAttempt a WHERE a.quiz.id IN :quizIds GROUP BY a.quiz.id")
  List<QuizCorrectRateRow> correctRateByQuizIds(@Param("quizIds") Collection<Long> quizIds);

  interface QuizCorrectRateRow {
    Long getQuizId();

    Double getRate();
  }
}
