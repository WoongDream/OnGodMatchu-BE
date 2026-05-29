package com.ongodmatchu.domain.quiz.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * imageKey / answerImageKey = 보여주는(크롭) 이미지. original*Key = 크롭 전 원본(재편집용, 크롭 안 했으면 cropped 와 동일 또는
 * null). *Transform = FE 소유 opaque 크롭/변환 JSON (BE 미해석).
 */
public record QuestionCreateRequest(
    String imageKey,
    String originalImageKey,
    JsonNode imageTransform,
    String answerImageKey,
    String originalAnswerImageKey,
    JsonNode answerImageTransform,
    String questionText,
    @NotBlank(message = "정답을 입력해주세요.") String answer) {}
