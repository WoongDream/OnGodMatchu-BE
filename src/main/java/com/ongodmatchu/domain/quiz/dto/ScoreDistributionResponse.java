package com.ongodmatchu.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 퀴즈 점수 분포. distribution 은 score 0~totalQuestions 전 칸 포함 (응시 없는 score 도 count=0). averageScore 는 소수
 * 1자리, totalAttempts=0 시 0.
 */
public record ScoreDistributionResponse(
    @Schema(description = "전체 응시 횟수 (익명 포함)") long totalAttempts,
    @Schema(description = "평균 점수 (소수 1자리). 응시 0 시 0") double averageScore,
    @Schema(description = "score 0~totalQuestions 전 칸 (count=0 포함)")
        List<ScoreCountResponse> distribution) {}
