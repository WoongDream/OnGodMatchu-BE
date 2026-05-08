package com.ongodmatchu.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 내 프로필 (내가 만든 퀴즈) 페이지 상단 통계. weeklyPlayCount / avgCorrectRate 는 attempts 기반 집계. */
public record ProfileStatsResponse(
    long totalQuizCount,
    long totalPlayCount,
    long totalStarCount,
    long totalCommentCount,
    long totalShareCount,
    @Schema(description = "이번 주(KST 월요일 0시~now) 본인 소유 퀴즈에 대한 attempts 수") long weeklyPlayCount,
    @Schema(
            description = "본인 퀴즈 전체 평균 정답률 (0~100). 산출: 시도 1회 이상 퀴즈의 단순 평균. 시도 1회 이상 퀴즈가 0개면 null",
            nullable = true)
        Double avgCorrectRate) {}
