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
  @DisplayName("enforceRequired_이용약관과개인정보모두동의_예외없음")
  void enforceRequired_bothAgreed_doesNotThrow() {
    assertThatCode(() -> TermsPolicy.enforceRequired(true, true)).doesNotThrowAnyException();
  }

  // ============ enforceRequired — 예외 ============

  @Test
  @DisplayName("enforceRequired_이용약관미동의_TERMS_AGREEMENT_REQUIRED예외")
  void enforceRequired_termsNotAgreed_throwsTermsAgreementRequired() {
    assertThatThrownBy(() -> TermsPolicy.enforceRequired(false, true))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TERMS_AGREEMENT_REQUIRED);
  }

  @Test
  @DisplayName("enforceRequired_개인정보미동의_TERMS_AGREEMENT_REQUIRED예외")
  void enforceRequired_privacyNotAgreed_throwsTermsAgreementRequired() {
    assertThatThrownBy(() -> TermsPolicy.enforceRequired(true, false))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TERMS_AGREEMENT_REQUIRED);
  }

  @Test
  @DisplayName("enforceRequired_이용약관과개인정보모두미동의_TERMS_AGREEMENT_REQUIRED예외")
  void enforceRequired_neitherAgreed_throwsTermsAgreementRequired() {
    assertThatThrownBy(() -> TermsPolicy.enforceRequired(false, false))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TERMS_AGREEMENT_REQUIRED);
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
