package com.ongodmatchu.domain.user.entity;

/** 회원탈퇴 사유 — 익명 통계용. DB 에는 코드 문자열만 저장한다. */
public enum WithdrawalReason {
  /** 더 이상 사용하지 않음 */
  NOT_USING,
  /** 원하는 기능이 부족함 */
  TOO_FEW_FEATURES,
  /** 개인정보/프라이버시 우려 */
  PRIVACY,
  /** 다른 서비스로 이동 */
  SWITCHED_SERVICE,
  /** 기타 (reasonText 자유 입력) */
  ETC
}
