package com.ongodmatchu.domain.quiz.repository;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

  /**
   * 메인 공개 목록 — visibility 한정 + 카테고리(옵션) + 제목 부분검색(옵션). 정렬은 Pageable 위임 (인기순 playCount,createdAt
   * DESC / 최신순 createdAt DESC). category·title 모두 null 이면 전체 PUBLIC. null bind 시 PostgreSQL bytea
   * 추론 오류를 CAST(... AS string) 으로 회피.
   */
  @Query(
      value =
          "SELECT q FROM Quiz q "
              + "WHERE q.visibility = :visibility "
              + "AND (CAST(:category AS string) IS NULL OR q.category = CAST(:category AS string)) "
              + "AND (CAST(:title AS string) IS NULL "
              + "OR LOWER(q.title) LIKE LOWER(CONCAT('%', CAST(:title AS string), '%')))",
      countQuery =
          "SELECT COUNT(q) FROM Quiz q "
              + "WHERE q.visibility = :visibility "
              + "AND (CAST(:category AS string) IS NULL OR q.category = CAST(:category AS string)) "
              + "AND (CAST(:title AS string) IS NULL "
              + "OR LOWER(q.title) LIKE LOWER(CONCAT('%', CAST(:title AS string), '%')))")
  Page<Quiz> searchPublic(
      @Param("visibility") QuizVisibility visibility,
      @Param("category") String category,
      @Param("title") String title,
      Pageable pageable);

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

  /** 타인 시점 프로필 요약용 — visibility 한정 집계 (PUBLIC 만 넘겨 비공개 퀴즈 제외). */
  @Query(
      "SELECT COUNT(q) AS quizCount, "
          + "COALESCE(SUM(q.playCount), 0) AS plays, "
          + "COALESCE(SUM(q.starCount), 0) AS stars, "
          + "COALESCE(SUM(q.commentCount), 0) AS comments, "
          + "COALESCE(SUM(q.shareCount), 0) AS shares "
          + "FROM Quiz q WHERE q.user.id = :userId AND q.visibility = :visibility")
  QuizAggregateRow aggregateByUserIdAndVisibility(
      @Param("userId") Long userId, @Param("visibility") QuizVisibility visibility);

  /**
   * 회원탈퇴 시 본인 소유 퀴즈를 시스템 관리자 계정으로 일괄 이전.
   *
   * <p>clearAutomatically=false — true 로 두면 호출자(`UserService.withdraw`) 가 미리 로드한 user 인스턴스까지 detach
   * 되어 이후 `user.withdraw(...)` 의 dirty checking 이 무시되고 users 행 UPDATE 가 누락된다.
   */
  @Modifying(flushAutomatically = true)
  @Query("UPDATE Quiz q SET q.user.id = :toUserId WHERE q.user.id = :fromUserId")
  int transferOwnership(@Param("fromUserId") Long fromUserId, @Param("toUserId") Long toUserId);

  /** 회원탈퇴 시 "내 퀴즈도 모두 삭제" 옵션용 — 작성자 본인의 모든 Quiz 행 조회 (S3 키 정리·연관 일괄 삭제 위해 ID 도 같이 사용). */
  List<Quiz> findAllByUserId(Long userId);

  /** 카테고리 목록 정렬용 — visibility 기준 카테고리별 총 플레이수 합계. 퀴즈 없는 카테고리는 행 자체가 없으므로 호출부에서 0 보정. */
  @Query(
      "SELECT q.category AS category, COALESCE(SUM(q.playCount), 0) AS plays "
          + "FROM Quiz q WHERE q.visibility = :visibility GROUP BY q.category")
  List<CategoryPlayCountRow> sumPlayCountByCategory(@Param("visibility") QuizVisibility visibility);

  interface CategoryPlayCountRow {
    String getCategory();

    long getPlays();
  }

  interface QuizAggregateRow {
    long getQuizCount();

    long getPlays();

    long getStars();

    long getComments();

    long getShares();
  }
}
