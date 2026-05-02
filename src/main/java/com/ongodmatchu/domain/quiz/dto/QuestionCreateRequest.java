package com.ongodmatchu.domain.quiz.dto;

import jakarta.validation.constraints.NotBlank;

public record QuestionCreateRequest(
    String imageKey,
    String answerImageKey,
    String questionText,
    @NotBlank(message = "정답을 입력해주세요.") String answer) {}
