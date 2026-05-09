package com.ongodmatchu.domain.user.dto;

/** 약관 동의 처리 요청 — 필수 약관(이용약관/개인정보처리방침) 동의는 호출 자체로 간주. */
public record TermsAgreementRequest(Boolean agreedToMarketing) {

  public boolean marketingOptIn() {
    return agreedToMarketing != null && agreedToMarketing;
  }
}
