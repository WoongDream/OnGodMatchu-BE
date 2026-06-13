package com.ongodmatchu.domain.nickname.entity;

/** 차단 닉네임 분류 지표. 금지 정책은 유형과 무관하게 동일(매칭 방식이 차단 강도를 결정). 예약어는 접두 일치, 금지어는 부분 일치가 기본 세팅. */
public enum ForbiddenNicknameType {
  /** 예약어 — 운영/시스템 사칭 방지용. 기본 매칭 PREFIX. */
  RESERVED,
  /** 금지어 — 비속어 등. 기본 매칭 CONTAINS. */
  FORBIDDEN
}
