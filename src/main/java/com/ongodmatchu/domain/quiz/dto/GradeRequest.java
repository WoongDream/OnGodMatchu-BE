package com.ongodmatchu.domain.quiz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GradeRequest(
    @NotNull(message = "문제 ID를 입력해주세요.") Long questionId,
    @NotBlank(message = "답변을 입력해주세요.") String userAnswer) {}
