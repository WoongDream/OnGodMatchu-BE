package com.ongodmatchu.domain.nickname.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NicknameMatchNormalizerTest {

  private final NicknameMatchNormalizer normalizer = new NicknameMatchNormalizer();

  // ============ Tier 0 — NFKC 전각→반각 + 소문자 ============

  @Test
  @DisplayName("normalize_전각ＡＤＭＩＮ_반각소문자admin")
  void normalize_fullWidthAdmin_toLowerHalfWidth() {
    assertThat(normalizer.normalize("ＡＤＭＩＮ")).isEqualTo("admin");
  }

  @Test
  @DisplayName("normalize_Admin_소문자admin")
  void normalize_mixedCase_toLower() {
    assertThat(normalizer.normalize("Admin")).isEqualTo("admin");
  }

  // ============ Tier 0 — 트림 + 내부 공백 제거 ============

  @Test
  @DisplayName("normalize_내부공백_관리 자_관리자")
  void normalize_innerSpace_stripped() {
    assertThat(normalizer.normalize("관리 자")).isEqualTo("관리자");
  }

  @Test
  @DisplayName("normalize_앞뒤공백_ 관리자 _관리자")
  void normalize_surroundingSpace_trimmed() {
    assertThat(normalizer.normalize(" 관리자 ")).isEqualTo("관리자");
  }

  // ============ Tier 1 — 구두점·언더스코어·zero-width 제거 ============

  @Test
  @DisplayName("normalize_마침표_병.신_병신")
  void normalize_dot_stripped() {
    assertThat(normalizer.normalize("병.신")).isEqualTo("병신");
  }

  @Test
  @DisplayName("normalize_언더스코어_병_신_병신")
  void normalize_underscore_stripped() {
    assertThat(normalizer.normalize("병_신")).isEqualTo("병신");
  }

  @Test
  @DisplayName("normalize_zero-width삽입_병\\u200B신_병신")
  void normalize_zeroWidth_stripped() {
    assertThat(normalizer.normalize("병​신")).isEqualTo("병신");
  }

  // ============ 경계 ============

  @Test
  @DisplayName("normalize_null_빈문자열")
  void normalize_null_returnsEmpty() {
    assertThat(normalizer.normalize(null)).isEmpty();
  }

  @Test
  @DisplayName("normalize_구두점만_빈문자열")
  void normalize_allPunctuation_returnsEmpty() {
    assertThat(normalizer.normalize("...___!!!")).isEmpty();
  }
}
