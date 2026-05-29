package com.ongodmatchu.domain.visit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VisitCreateRequest(
    @Schema(description = "방문한 SPA 경로 (예: /quiz, /quiz/abc-123)", example = "/quiz")
        @NotBlank
        @Size(max = 255)
        String path) {}
