package com.ongodmatchu.domain.user.repository;

import com.ongodmatchu.domain.user.entity.Role;
import com.ongodmatchu.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  Optional<User> findByPublicId(UUID publicId);

  boolean existsByEmail(String email);

  boolean existsByNickname(String nickname);

  /**
   * 백오피스 사용자 목록. 시스템 계정 제외. status 미지정 시 탈퇴자 숨김(활성만). status 는 파생값이라 SQL 에서 직접 판정한다 (SUSPENDED = 활성
   * + 정지기한 유효). nickname/email 부분검색은 null bind 시 bytea 캐스팅 오류 회피를 위해 CAST(:query AS string) 사용.
   */
  @Query(
      """
      SELECT u FROM User u
      WHERE u.isSystem = false
        AND ( (:status IS NULL AND u.isActive = true)
           OR (:status = 'WITHDRAWN' AND u.isActive = false)
           OR (:status = 'SUSPENDED' AND u.isActive = true
                 AND u.suspendedUntil IS NOT NULL AND u.suspendedUntil > :now)
           OR (:status = 'ACTIVE' AND u.isActive = true
                 AND (u.suspendedUntil IS NULL OR u.suspendedUntil <= :now)) )
        AND (:role IS NULL OR u.role = :role)
        AND (CAST(:query AS string) IS NULL
           OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
           OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')))
      """)
  Page<User> searchForAdmin(
      @Param("status") String status,
      @Param("role") Role role,
      @Param("query") String query,
      @Param("now") LocalDateTime now,
      Pageable pageable);

  /** 통계 카드 — 활성(비탈퇴) 유저 수, 시스템 제외. */
  @Query("SELECT COUNT(u) FROM User u WHERE u.isSystem = false AND u.isActive = true")
  long countActive();

  /** 통계 카드 — 역할별 활성 유저 수, 시스템 제외. */
  @Query(
      "SELECT COUNT(u) FROM User u "
          + "WHERE u.isSystem = false AND u.isActive = true AND u.role = :role")
  long countActiveByRole(@Param("role") Role role);

  /** 통계 카드 — 현재 정지 중(활성 + 정지기한 유효) 유저 수, 시스템 제외. */
  @Query(
      "SELECT COUNT(u) FROM User u "
          + "WHERE u.isSystem = false AND u.isActive = true "
          + "AND u.suspendedUntil IS NOT NULL AND u.suspendedUntil > :now")
  long countSuspended(@Param("now") LocalDateTime now);

  /** 월별 통계 — [start, end) 구간 신규 가입 수, 시스템 제외. */
  @Query(
      "SELECT COUNT(u) FROM User u "
          + "WHERE u.isSystem = false AND u.createdAt >= :start AND u.createdAt < :end")
  long countJoinedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  /** 월별 통계 — [start, end) 구간 이탈(탈퇴) 수, 시스템 제외. */
  @Query(
      "SELECT COUNT(u) FROM User u "
          + "WHERE u.isSystem = false AND u.deletedAt IS NOT NULL "
          + "AND u.deletedAt >= :start AND u.deletedAt < :end")
  long countWithdrawnBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  /** 월별 통계 — asOf 시점의 누적 활성 유저 수 (그 전에 가입 + asOf 시점까지 미탈퇴), 시스템 제외. */
  @Query(
      "SELECT COUNT(u) FROM User u "
          + "WHERE u.isSystem = false AND u.createdAt < :asOf "
          + "AND (u.deletedAt IS NULL OR u.deletedAt >= :asOf)")
  long countActiveAsOf(@Param("asOf") LocalDateTime asOf);
}
