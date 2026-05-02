package com.ongodmatchu.domain.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record QuizCreateRequest(
    @NotBlank(message = "제목을 입력해주세요.") String title,
    String description,
    @NotBlank(message = "카테고리를 입력해주세요.") String category,
    String thumbnailKey,
    @NotEmpty(message = "문제를 1개 이상 입력해주세요.") @Valid List<QuestionCreateRequest> questions) {}
