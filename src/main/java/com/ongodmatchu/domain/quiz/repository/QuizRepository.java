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

  interface QuizAggregateRow {
    long getQuizCount();

    long getPlays();

    long getStars();

    long getComments();

    long getShares();
  }
}
