package com.ongodmatchu.domain.quiz.dto;

import jakarta.validation.constraints.Size;

/** 퀴즈 메타 수정. null 인 필드는 변경 없음 (PATCH). 문제 본문/이미지 수정은 1차 범위 외. */
public record QuizUpdateRequest(
    @Size(min = 1, max = 255, message = "제목은 1~255자여야 합니다.") String title,
    String description,
    String category,
    String thumbnailKey) {}
