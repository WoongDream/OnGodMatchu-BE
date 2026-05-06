package com.ongodmatchu.domain.user.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NicknamePolicyTest {

  private NicknamePolicy nicknamePolicy;

  @BeforeEach
  void setUp() {
    nicknamePolicy = new NicknamePolicy();
  }

  // ============ enforce — 정상 통과 ============

  @Test
  @DisplayName("enforce_한글닉네임_통과")
  void enforce_koreanNickname_doesNotThrow() {
    nicknamePolicy.enforce("홍길동");
  }

  @Test
  @DisplayName("enforce_영문소문자숫자닉네임_통과")
  void enforce_alphanumericNickname_doesNotThrow() {
    nicknamePolicy.enforce("alice123");
  }

  @Test
  @DisplayName("enforce_한글숫자혼합닉네임_통과")
  void enforce_koreanWithNumbers_doesNotThrow() {
    nicknamePolicy.enforce("판다1234");
  }

  @Test
  @DisplayName("enforce_언더스코어포함닉네임_통과")
  void enforce_underscoreIncluded_doesNotThrow() {
    nicknamePolicy.enforce("user_01");
  }

  @Test
  @DisplayName("enforce_경계값2자_통과")
  void enforce_minLengthBoundary_doesNotThrow() {
    nicknamePolicy.enforce("ab");
  }

  @Test
  @DisplayName("enforce_경계값10자_통과")
  void enforce_maxLengthBoundary_doesNotThrow() {
    nicknamePolicy.enforce("a".repeat(10));
  }

  // ============ enforce — 실패 ============

  @Test
  @DisplayName("enforce_null입력_INVALID_NICKNAME_FORMAT예외")
  void enforce_null_throwsInvalidFormat() {
    assertThatThrownBy(() -> nicknamePolicy.enforce(null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_NICKNAME_FORMAT);
  }

  @Test
  @DisplayName("enforce_빈문자열_INVALID_NICKNAME_FORMAT예외")
  void enforce_emptyString_throwsInvalidFormat() {
    assertThatThrownBy(() -> nicknamePolicy.enforce(""))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_NICKNAME_FORMAT);
  }

  @Test
  @DisplayName("enforce_공백만있는문자열_INVALID_NICKNAME_FORMAT예외")
  void enforce_blankString_throwsInvalidFormat() {
    assertThatThrownBy(() -> nicknamePolicy.enforce("   "))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_NICKNAME_FORMAT);
  }

  @Test
  @DisplayName("enforce_길이1자_너무짧음_예외")
  void enforce_tooShort_throwsInvalidFormat() {
    assertThatThrownBy(() -> nicknamePolicy.enforce("a"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_NICKNAME_FORMAT);
  }

  @Test
  @DisplayName("enforce_길이11자_너무긴_예외")
  void enforce_tooLong_throwsInvalidFormat() {
    assertThatThrownBy(() -> nicknamePolicy.enforce("a".repeat(11)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_NICKNAME_FORMAT);
  }

  @Test
  @DisplayName("enforce_내부공백포함_예외")
  void enforce_containsSpace_throwsInvalidFormat() {
    assertThatThrownBy(() -> nicknamePolicy.enforce("hello world"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_NICKNAME_FORMAT);
  }

  @Test
  @DisplayName("enforce_특수문자포함_예외")
  void enforce_containsSpecialChar_throwsInvalidFormat() {
    assertThatThrownBy(() -> nicknamePolicy.enforce("user!"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_NICKNAME_FORMAT);
  }

  @Test
  @DisplayName("enforce_이모지포함_예외")
  void enforce_containsEmoji_throwsInvalidFormat() {
    assertThatThrownBy(() -> nicknamePolicy.enforce("u😀"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_NICKNAME_FORMAT);
  }

  // ============ isValid — sanity check ============

  @Test
  @DisplayName("isValid_유효한닉네임_true반환")
  void isValid_validNickname_returnsTrue() {
    assertThat(nicknamePolicy.isValid("판다1234")).isTrue();
  }

  @Test
  @DisplayName("isValid_null입력_false반환")
  void isValid_null_returnsFalse() {
    assertThat(nicknamePolicy.isValid(null)).isFalse();
  }

  @Test
  @DisplayName("isValid_길이1자닉네임_false반환")
  void isValid_tooShort_returnsFalse() {
    assertThat(nicknamePolicy.isValid("a")).isFalse();
  }
}
