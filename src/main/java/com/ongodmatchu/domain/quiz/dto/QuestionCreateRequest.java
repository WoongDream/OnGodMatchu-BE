package com.ongodmatchu.domain.quiz.dto;

import jakarta.validation.constraints.NotBlank;

public record QuestionCreateRequest(
    String imageUrl, String questionText, @NotBlank(message = "정답을 입력해주세요.") String answer) {}
