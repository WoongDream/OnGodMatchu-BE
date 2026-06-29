package com.ongodmatchu.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record AttemptCreateRequest(
    @NotEmpty(message = "답변은 1개 이상 입력해주세요.") @Valid List<AttemptAnswerRequest> answers,
    @Schema(description = "풀이 당시 문항당 타이머 설정(초). 타이머 없음이면 생략/null", nullable = true) @Positive
        Integer timeLimitSec) {}
