package com.ongodmatchu.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 내 프로필 (내가 만든 퀴즈) 페이지 상단 통계. {@code weeklyPlayCount} 는 풀이 기록 묶음 머지 전까지 0 반환. */
public record ProfileStatsResponse(
    long totalQuizCount,
    long totalPlayCount,
    long totalStarCount,
    long totalCommentCount,
    long totalShareCount,
    @Schema(description = "이번 주(KST 월요일 0시~now) 플레이 수. 1차에서는 항상 0 — 풀이 기록 묶음 머지 후 채움")
        long weeklyPlayCount) {}
