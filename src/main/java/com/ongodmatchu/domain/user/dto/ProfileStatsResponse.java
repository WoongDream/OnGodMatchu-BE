package com.ongodmatchu.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 프로필 페이지 상단 통계. 만든 퀴즈 통계(total*) + 만든 퀴즈가 받은 평균 정답률(avgCorrectRate) + 내가 푼 기록(solvedCount /
 * avgSolveRate). weeklyPlayCount / avgCorrectRate / avgSolveRate 는 attempts 기반 집계.
 */
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
        Double avgCorrectRate,
    @Schema(description = "본인이 푼 횟수 (본인 user_id 의 attempts 수, 비로그인 풀이 제외)") long solvedCount,
    @Schema(
            description =
                "본인이 푼 평균 정답률 (0~100). 산출: 본인 attempts 의 SUM(score)*100/SUM(totalQuestions). 시도 0이면 null",
            nullable = true)
        Double avgSolveRate) {}
