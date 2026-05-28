package com.ongodmatchu.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record QuizShareResponse(
    @Schema(description = "공유 카운트 (사용자/익명 단위 중복 제외)") long shareCount,
    @Schema(description = "현재 식별자(user_id 또는 anon_id)가 이미 공유한 경우 true. 클라이언트 공유 동작은 그대로 진행")
        boolean alreadyShared) {}
