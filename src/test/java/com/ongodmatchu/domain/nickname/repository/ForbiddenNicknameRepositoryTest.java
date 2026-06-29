package com.ongodmatchu.domain.nickname.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ongodmatchu.domain.nickname.entity.ForbiddenNickname;
import com.ongodmatchu.domain.nickname.entity.ForbiddenNicknameType;
import com.ongodmatchu.domain.nickname.entity.NicknameMatchType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ForbiddenNicknameRepositoryTest {

  @Autowired private ForbiddenNicknameRepository repository;
  @Autowired private EntityManager em;

  // V36 시드 2행('관리자','탈퇴한사용자')과 격리 — 각 테스트는 빈 테이블에서 시작.
  @BeforeEach
  void clean() {
    repository.deleteAllInBatch();
    em.flush();
    em.clear();
  }

  private ForbiddenNickname save(
      String raw, String normalized, ForbiddenNicknameType type, NicknameMatchType matchType) {
    ForbiddenNickname rule =
        ForbiddenNickname.builder()
            .rawValue(raw)
            .normalizedValue(normalized)
            .type(type)
            .matchType(matchType)
            .build();
    return repository.save(rule);
  }

  // ============ existsBlocking — EXACT ============

  @Test
  @DisplayName("existsBlocking_EXACT_관리자_완전일치만차단")
  void existsBlocking_exact() {
    save("관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    em.flush();

    assertThat(repository.existsBlocking("관리자")).isTrue();
    assertThat(repository.existsBlocking("관리자123")).isFalse();
    assertThat(repository.existsBlocking("운영관리자")).isFalse();
  }

  // ============ existsBlocking — PREFIX ============

  @Test
  @DisplayName("existsBlocking_PREFIX_관리자_접두일치차단")
  void existsBlocking_prefix() {
    save("관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.PREFIX);
    em.flush();

    assertThat(repository.existsBlocking("관리자")).isTrue();
    assertThat(repository.existsBlocking("관리자123")).isTrue();
    assertThat(repository.existsBlocking("운영관리자")).isFalse();
  }

  // ============ existsBlocking — CONTAINS ============

  @Test
  @DisplayName("existsBlocking_CONTAINS_병신_부분일치차단")
  void existsBlocking_contains() {
    save("병신", "병신", ForbiddenNicknameType.FORBIDDEN, NicknameMatchType.CONTAINS);
    em.flush();

    assertThat(repository.existsBlocking("병신")).isTrue();
    assertThat(repository.existsBlocking("병신abc")).isTrue();
    assertThat(repository.existsBlocking("abc병신def")).isTrue();
    assertThat(repository.existsBlocking("병환")).isFalse();
  }

  @Test
  @DisplayName("existsBlocking_매칭없으면_false")
  void existsBlocking_noMatch() {
    save("관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    em.flush();

    assertThat(repository.existsBlocking("일반유저")).isFalse();
  }

  // ============ searchForAdmin ============

  @Test
  @DisplayName("searchForAdmin_type와q모두null이면_전체조회")
  void searchForAdmin_allNull_returnsAll() {
    save("관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    save("병신", "병신", ForbiddenNicknameType.FORBIDDEN, NicknameMatchType.CONTAINS);
    em.flush();

    Page<ForbiddenNickname> page = repository.searchForAdmin(null, null, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(2L);
  }

  @Test
  @DisplayName("searchForAdmin_type필터_FORBIDDEN만조회")
  void searchForAdmin_typeFilter() {
    save("관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    ForbiddenNickname forbidden =
        save("병신", "병신", ForbiddenNicknameType.FORBIDDEN, NicknameMatchType.CONTAINS);
    em.flush();

    Page<ForbiddenNickname> page =
        repository.searchForAdmin(ForbiddenNicknameType.FORBIDDEN, null, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent().get(0).getId()).isEqualTo(forbidden.getId());
  }

  @Test
  @DisplayName("searchForAdmin_q는_원본_정규화값_대소문자무시_부분일치")
  void searchForAdmin_queryFilter() {
    ForbiddenNickname admin =
        save("Admin", "admin", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    save("병신", "병신", ForbiddenNicknameType.FORBIDDEN, NicknameMatchType.CONTAINS);
    em.flush();

    // 원본/정규화값 대소문자 무시 부분일치
    Page<ForbiddenNickname> filtered =
        repository.searchForAdmin(null, "ADMIN", PageRequest.of(0, 10));
    assertThat(filtered.getTotalElements()).isEqualTo(1L);
    assertThat(filtered.getContent().get(0).getId()).isEqualTo(admin.getId());
  }

  @Test
  @DisplayName("searchForAdmin_q가null이면_CAST가드로_전체조회 (null bind 안전)")
  void searchForAdmin_nullQuery_returnsAll() {
    save("관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    save("병신", "병신", ForbiddenNicknameType.FORBIDDEN, NicknameMatchType.CONTAINS);
    em.flush();

    Page<ForbiddenNickname> page = repository.searchForAdmin(null, null, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(2L);
  }

  @Test
  @DisplayName("searchForAdmin_최신등록순_createdAt내림차순")
  void searchForAdmin_orderByCreatedAtDesc() {
    ForbiddenNickname first =
        save("first", "first", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    em.flush();
    ForbiddenNickname second =
        save("second", "second", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    em.flush();

    Page<ForbiddenNickname> page = repository.searchForAdmin(null, null, PageRequest.of(0, 10));

    assertThat(page.getContent())
        .extracting(ForbiddenNickname::getId)
        .containsExactly(second.getId(), first.getId());
  }

  // ============ countByType ============

  @Test
  @DisplayName("countByType_유형별집계")
  void countByType() {
    save("관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    save("운영자", "운영자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    save("병신", "병신", ForbiddenNicknameType.FORBIDDEN, NicknameMatchType.CONTAINS);
    em.flush();

    assertThat(repository.countByType(ForbiddenNicknameType.RESERVED)).isEqualTo(2L);
    assertThat(repository.countByType(ForbiddenNicknameType.FORBIDDEN)).isEqualTo(1L);
  }

  // ============ existsByNormalizedValueAndMatchType / findByNormalizedValueAndMatchType
  // ============

  @Test
  @DisplayName("existsByNormalizedValueAndMatchType_정규화값과매칭방식둘다일치해야true")
  void existsByNormalizedValueAndMatchType() {
    save("관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    em.flush();

    assertThat(repository.existsByNormalizedValueAndMatchType("관리자", NicknameMatchType.EXACT))
        .isTrue();
    // 매칭 방식 다르면 false
    assertThat(repository.existsByNormalizedValueAndMatchType("관리자", NicknameMatchType.PREFIX))
        .isFalse();
    // 정규화값 다르면 false
    assertThat(repository.existsByNormalizedValueAndMatchType("운영자", NicknameMatchType.EXACT))
        .isFalse();
  }

  @Test
  @DisplayName("findByNormalizedValueAndMatchType_일치행반환_없으면empty")
  void findByNormalizedValueAndMatchType() {
    ForbiddenNickname saved =
        save("관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    em.flush();

    assertThat(repository.findByNormalizedValueAndMatchType("관리자", NicknameMatchType.EXACT))
        .isPresent()
        .get()
        .extracting(ForbiddenNickname::getId)
        .isEqualTo(saved.getId());
    assertThat(repository.findByNormalizedValueAndMatchType("관리자", NicknameMatchType.PREFIX))
        .isEmpty();
  }
}
