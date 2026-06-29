package com.ongodmatchu.domain.nickname.entity;

/** 차단 매칭 방식. 비교는 모두 정규화(Tier 0·1)된 값 기준. */
public enum NicknameMatchType {
  /** 완전 일치 — 정규화 후 동일해야 차단. */
  EXACT,
  /** 접두 일치 — 정규화 후보가 패턴으로 시작하면 차단('관리자' → '관리자123' 차단, '운영관리자' 허용). */
  PREFIX,
  /** 부분 일치 — 정규화 후보 어디든 패턴을 포함하면 차단('병신' → '병신*', '*병신*' 모두 차단). */
  CONTAINS
}
