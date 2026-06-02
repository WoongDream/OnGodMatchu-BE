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
import org.springframework.transaction.annotation.Transactional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

  Page<QuizAttempt> findByUserIdOrderByCompletedAtDesc(Long userId, Pageable pageable);

  /**
   * 내 풀이 기록을 퀴즈 단위로 그룹화 — quiz 당 1행 (quizId + 누적 풀이 횟수), 최신 풀이 시각 DESC 정렬. title 이 주어지면 퀴즈 제목 부분일치
   * 필터(대소문자 무시). 정렬은 쿼리에 박혀 있으므로 호출자는 정렬 없는 Pageable 을 넘긴다.
   */
  @Query(
      value =
          "SELECT a.quiz.id AS quizId, COUNT(a) AS attemptCount "
              + "FROM QuizAttempt a "
              + "WHERE a.user.id = :userId "
              + "AND (CAST(:title AS string) IS NULL OR LOWER(a.quiz.title) LIKE LOWER(CONCAT('%', CAST(:title AS string), '%'))) "
              + "GROUP BY a.quiz.id "
              + "ORDER BY MAX(a.completedAt) DESC",
      countQuery =
          "SELECT COUNT(DISTINCT a.quiz.id) FROM QuizAttempt a "
              + "WHERE a.user.id = :userId "
              + "AND (CAST(:title AS string) IS NULL OR LOWER(a.quiz.title) LIKE LOWER(CONCAT('%', CAST(:title AS string), '%')))")
  Page<AttemptGroupRow> findAttemptGroupsByUserId(
      @Param("userId") Long userId, @Param("title") String title, Pageable pageable);

  /**
   * 주어진 퀴즈 ID 들에 대해 해당 유저의 최신 attempt 1건씩 (quiz fetch join). 최신 기준은 MAX(id) — IDENTITY 단조 증가라
   * completedAt 동률에도 안전.
   */
  @Query(
      "SELECT a FROM QuizAttempt a JOIN FETCH a.quiz "
          + "WHERE a.user.id = :userId AND a.quiz.id IN :quizIds "
          + "AND a.id = (SELECT MAX(b.id) FROM QuizAttempt b "
          + "WHERE b.user.id = :userId AND b.quiz.id = a.quiz.id)")
  List<QuizAttempt> findLatestAttemptsPerQuiz(
      @Param("userId") Long userId, @Param("quizIds") Collection<Long> quizIds);

  interface AttemptGroupRow {
    Long getQuizId();

    long getAttemptCount();
  }

  long countByQuizId(Long quizId);

  @Transactional
  void deleteByQuizIdIn(Collection<Long> quizIds);

  /** 점수 분포 — score 별 응시 수. 빈 score 는 결과에 미포함(Service 단에서 0 채움). */
  @Query(
      "SELECT a.score AS score, COUNT(a) AS count "
          + "FROM QuizAttempt a WHERE a.quiz.id = :quizId GROUP BY a.score")
  List<ScoreBucketRow> findScoreDistributionByQuizId(@Param("quizId") Long quizId);

  /** 동률 중간 처리 백분위용 — 본인보다 score 가 높은 응시 수. */
  @Query("SELECT COUNT(a) FROM QuizAttempt a " + "WHERE a.quiz.id = :quizId AND a.score > :score")
  long countByQuizIdAndScoreGreaterThan(@Param("quizId") Long quizId, @Param("score") int score);

  /** 동률 중간 처리 백분위용 — 본인과 score 가 같은 응시 수 (본인 attempt 포함). */
  @Query("SELECT COUNT(a) FROM QuizAttempt a " + "WHERE a.quiz.id = :quizId AND a.score = :score")
  long countByQuizIdAndScore(@Param("quizId") Long quizId, @Param("score") int score);

  interface ScoreBucketRow {
    Integer getScore();

    Long getCount();
  }

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

  /** 본인이 푼 횟수 — user_id 로 필터하므로 비로그인(user=null) attempt 는 자연 제외. */
  long countByUserId(Long userId);

  /**
   * 본인이 푼 평균 정답률 (0~100). 산출: 본인 attempts 의 SUM(score)*100/SUM(totalQuestions). 시도 0이면 SUM 이 null
   * 이라 null 반환.
   */
  @Query(
      "SELECT (SUM(a.score) * 100.0 / SUM(a.totalQuestions)) "
          + "FROM QuizAttempt a WHERE a.user.id = :userId")
  Double avgSolveRateOf(@Param("userId") Long userId);

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
