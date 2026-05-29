package com.ongodmatchu.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 풀이 1회 결과. 비로그인 시 attemptId 만 null (DB 저장은 익명으로 진행되어 작성자 통계에는 반영). */
public record AttemptResultResponse(
    @Schema(
            description = "본인의 attempt id. 비로그인 풀이 시 null (DB 에는 익명으로 저장되어 작성자 통계에 반영)",
            nullable = true)
        Long attemptId,
    int score,
    int totalQuestions,
    @Schema(description = "정답률 0~100. 문제가 0개면 null", nullable = true) Double percent,
    @Schema(
            description =
                "상위 백분위 (0~100, 소수 1자리). 본인 attempt 포함 + 동률 중간 처리"
                    + " (countGreaterThan(myScore) + countEqual(myScore)/2) / totalAttempts * 100."
                    + " 본인이 첫 응시자(totalAttempts=1)면 null",
            nullable = true)
        Double topPercentile,
    List<AttemptItemResultResponse> results) {}
