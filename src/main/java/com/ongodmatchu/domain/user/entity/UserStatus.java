package com.ongodmatchu.domain.user.entity;

/** 사용자 상태. 별도 컬럼 없이 기존 필드에서 파생한다 — WITHDRAWN=비활성(soft delete), SUSPENDED=정지 기한 유효, 그 외 ACTIVE. */
public enum UserStatus {
  ACTIVE,
  SUSPENDED,
  WITHDRAWN
}
