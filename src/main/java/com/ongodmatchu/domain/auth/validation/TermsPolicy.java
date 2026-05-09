package com.ongodmatchu.domain.auth.validation;

import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;

/** 약관 / 개인정보처리방침 버전 카탈로그. 본문이 변경되면 상수만 올린다. */
public final class TermsPolicy {

  public static final String CURRENT_TERMS_VERSION = "1.0";
  public static final String CURRENT_PRIVACY_VERSION = "1.0";

  private TermsPolicy() {}

  public static void enforceRequired(boolean agreedToTerms, boolean agreedToPrivacy) {
    if (!agreedToTerms || !agreedToPrivacy) {
      throw new BusinessException(ErrorCode.TERMS_AGREEMENT_REQUIRED);
    }
  }
}
