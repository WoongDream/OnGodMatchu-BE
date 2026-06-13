package com.ongodmatchu.domain.nickname.repository;

import com.ongodmatchu.domain.nickname.entity.ForbiddenNickname;
import com.ongodmatchu.domain.nickname.entity.ForbiddenNicknameType;
import com.ongodmatchu.domain.nickname.entity.NicknameMatchType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForbiddenNicknameRepository extends JpaRepository<ForbiddenNickname, Long> {

  /**
   * 정규화된 후보 닉네임이 차단 규칙에 걸리는지. 매칭 방식별로 — 완전(동일) / 접두(후보가 패턴으로 시작) / 부분(후보가 패턴 포함). normalizedValue 는
   * 문자·숫자만 남아 LIKE 와일드카드(%, _) 가 없으므로 안전.
   */
  @Query(
      """
      SELECT COUNT(f) > 0 FROM ForbiddenNickname f
      WHERE (f.matchType = com.ongodmatchu.domain.nickname.entity.NicknameMatchType.EXACT
              AND f.normalizedValue = :cand)
         OR (f.matchType = com.ongodmatchu.domain.nickname.entity.NicknameMatchType.PREFIX
              AND :cand LIKE CONCAT(f.normalizedValue, '%'))
         OR (f.matchType = com.ongodmatchu.domain.nickname.entity.NicknameMatchType.CONTAINS
              AND :cand LIKE CONCAT('%', f.normalizedValue, '%'))
      """)
  boolean existsBlocking(@Param("cand") String cand);

  /**
   * {@link #existsBlocking} 와 동일 매칭 — 차단한 규칙 행을 반환(어느 단어 때문에 막혔는지 안내용). 가장 먼저 등록된 규칙 우선. 보통 {@code
   * PageRequest.of(0, 1)} 로 1건만 조회.
   */
  @Query(
      """
      SELECT f FROM ForbiddenNickname f
      WHERE (f.matchType = com.ongodmatchu.domain.nickname.entity.NicknameMatchType.EXACT
              AND f.normalizedValue = :cand)
         OR (f.matchType = com.ongodmatchu.domain.nickname.entity.NicknameMatchType.PREFIX
              AND :cand LIKE CONCAT(f.normalizedValue, '%'))
         OR (f.matchType = com.ongodmatchu.domain.nickname.entity.NicknameMatchType.CONTAINS
              AND :cand LIKE CONCAT('%', f.normalizedValue, '%'))
      ORDER BY f.id ASC
      """)
  List<ForbiddenNickname> findBlocking(@Param("cand") String cand, Pageable pageable);

  /** 백오피스 목록 — type 필터 + 원본/정규화값 검색(null bind bytea 회피로 CAST(:q AS string)). 최신 등록순. */
  @Query(
      """
      SELECT f FROM ForbiddenNickname f
      WHERE (:type IS NULL OR f.type = :type)
        AND (CAST(:q AS string) IS NULL
           OR LOWER(f.rawValue) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
           OR f.normalizedValue LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
      ORDER BY f.createdAt DESC
      """)
  Page<ForbiddenNickname> searchForAdmin(
      @Param("type") ForbiddenNicknameType type, @Param("q") String q, Pageable pageable);

  long countByType(ForbiddenNicknameType type);

  boolean existsByNormalizedValueAndMatchType(String normalizedValue, NicknameMatchType matchType);

  Optional<ForbiddenNickname> findByNormalizedValueAndMatchType(
      String normalizedValue, NicknameMatchType matchType);
}
