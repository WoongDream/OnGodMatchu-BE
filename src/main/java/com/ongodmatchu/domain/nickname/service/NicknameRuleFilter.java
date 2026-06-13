package com.ongodmatchu.domain.nickname.service;

import com.ongodmatchu.domain.nickname.entity.ForbiddenNicknameType;

/** 백오피스 목록 필터 탭 — 전체 / 금지 / 예약. */
public enum NicknameRuleFilter {
  ALL(null),
  FORBIDDEN(ForbiddenNicknameType.FORBIDDEN),
  RESERVED(ForbiddenNicknameType.RESERVED);

  private final ForbiddenNicknameType type;

  NicknameRuleFilter(ForbiddenNicknameType type) {
    this.type = type;
  }

  public ForbiddenNicknameType type() {
    return type;
  }
}
