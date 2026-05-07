package com.ongodmatchu.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 풀이 1회 결과. 비로그인 시 attemptId 만 null, 그 외 필드는 정상 응답. */
public record AttemptResultResponse(
    @Schema(description = "저장된 attempt id. 비로그인 풀이 시 null", nullable = true) Long attemptId,
    int score,
    int totalQuestions,
    @Schema(description = "정답률 0~100. 문제가 0개면 null", nullable = true) Double percent,
    List<AttemptItemResultResponse> results) {}
