package com.ongodmatchu.domain.auth.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TermsPolicyTest {

  // ============ enforceRequired — 정상 통과 ============

  @Test
  @DisplayName("enforceRequired_이용약관_개인정보_만14세모두동의_예외없음")
  void enforceRequired_allAgreed_doesNotThrow() {
    assertThatCode(() -> TermsPolicy.enforceRequired(true, true, true)).doesNotThrowAnyException();
  }

  // ============ enforceRequired — 예외 ============

  @Test
  @DisplayName("enforceRequired_이용약관미동의_TERMS_AGREEMENT_REQUIRED예외")
  void enforceRequired_termsNotAgreed_throwsTermsAgreementRequired() {
    assertThatThrownBy(() -> TermsPolicy.enforceRequired(false, true, true))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TERMS_AGREEMENT_REQUIRED);
  }

  @Test
  @DisplayName("enforceRequired_개인정보미동의_TERMS_AGREEMENT_REQUIRED예외")
  void enforceRequired_privacyNotAgreed_throwsTermsAgreementRequired() {
    assertThatThrownBy(() -> TermsPolicy.enforceRequired(true, false, true))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TERMS_AGREEMENT_REQUIRED);
  }

  @Test
  @DisplayName("enforceRequired_만14세미동의_TERMS_AGREEMENT_REQUIRED예외")
  void enforceRequired_age14NotAgreed_throwsTermsAgreementRequired() {
    assertThatThrownBy(() -> TermsPolicy.enforceRequired(true, true, false))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TERMS_AGREEMENT_REQUIRED);
  }

  @Test
  @DisplayName("enforceRequired_셋다미동의_TERMS_AGREEMENT_REQUIRED예외")
  void enforceRequired_noneAgreed_throwsTermsAgreementRequired() {
    assertThatThrownBy(() -> TermsPolicy.enforceRequired(false, false, false))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TERMS_AGREEMENT_REQUIRED);
  }

  // ============ needsAgreement — 동의 필요 여부 ============

  @Test
  @DisplayName("needsAgreement_termsVersion이NULL_true반환")
  void needsAgreement_termsVersionNull_returnsTrue() {
    assertThat(TermsPolicy.needsAgreement(null, TermsPolicy.CURRENT_PRIVACY_VERSION)).isTrue();
  }

  @Test
  @DisplayName("needsAgreement_privacyVersion이NULL_true반환")
  void needsAgreement_privacyVersionNull_returnsTrue() {
    assertThat(TermsPolicy.needsAgreement(TermsPolicy.CURRENT_TERMS_VERSION, null)).isTrue();
  }

  @Test
  @DisplayName("needsAgreement_둘다NULL_true반환")
  void needsAgreement_bothNull_returnsTrue() {
    assertThat(TermsPolicy.needsAgreement(null, null)).isTrue();
  }

  @Test
  @DisplayName("needsAgreement_구버전동의_true반환")
  void needsAgreement_outdatedVersion_returnsTrue() {
    assertThat(TermsPolicy.needsAgreement("0.9", "0.9")).isTrue();
  }

  @Test
  @DisplayName("needsAgreement_현재버전동의_false반환")
  void needsAgreement_currentVersion_returnsFalse() {
    assertThat(
            TermsPolicy.needsAgreement(
                TermsPolicy.CURRENT_TERMS_VERSION, TermsPolicy.CURRENT_PRIVACY_VERSION))
        .isFalse();
  }

  // ============ 상수 값 검증 ============

  @Test
  @DisplayName("CURRENT_TERMS_VERSION_상수값이_1.0임")
  void currentTermsVersion_isExpectedValue() {
    assertThat(TermsPolicy.CURRENT_TERMS_VERSION).isEqualTo("1.0");
  }

  @Test
  @DisplayName("CURRENT_PRIVACY_VERSION_상수값이_1.0임")
  void currentPrivacyVersion_isExpectedValue() {
    assertThat(TermsPolicy.CURRENT_PRIVACY_VERSION).isEqualTo("1.0");
  }
}
