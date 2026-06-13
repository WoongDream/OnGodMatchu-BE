package com.ongodmatchu.domain.nickname.dto;

/** 백오피스 상단 stats 카드 — 전체 규칙 / 금지 / 예약. */
public record NicknameRuleStatsResponse(long total, long forbidden, long reserved) {}
