package com.ongodmatchu.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AttemptItemResultResponse(
    Long questionId,
    boolean correct,
    String correctAnswer,
    String userAnswer,
    @Schema(
            description = "정답 이미지 presigned GET URL (1h TTL). answerImageKey 없으면 null",
            nullable = true)
        String correctAnswerImageUrl) {}
