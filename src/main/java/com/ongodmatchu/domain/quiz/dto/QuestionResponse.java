package com.ongodmatchu.domain.quiz.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.ongodmatchu.domain.question.entity.Question;
import io.swagger.v3.oas.annotations.media.Schema;

public record QuestionResponse(
    Long id,
    int orderNum,
    String imageKey,
    String imageUrl,
    @Schema(description = "문제 이미지 크롭 전 원본 key. 소유자(편집)에게만, 재편집 시 재전송용", nullable = true)
        String originalImageKey,
    @Schema(description = "문제 이미지 크롭 전 원본 presigned URL. 소유자에게만. 원본 미보존이면 null", nullable = true)
        String originalImageUrl,
    @JsonRawValue @Schema(description = "문제 이미지 크롭/변환 파라미터 (opaque JSON). 소유자에게만", nullable = true)
        String imageTransform,
    String answerImageKey,
    String answerImageUrl,
    @Schema(description = "정답 이미지 크롭 전 원본 key. 소유자에게만", nullable = true)
        String originalAnswerImageKey,
    @Schema(description = "정답 이미지 크롭 전 원본 presigned URL. 소유자에게만", nullable = true)
        String originalAnswerImageUrl,
    @JsonRawValue @Schema(description = "정답 이미지 크롭/변환 파라미터 (opaque JSON). 소유자에게만", nullable = true)
        String answerImageTransform,
    String questionText,
    String answer) {

  /**
   * @param includeOriginal 소유자(편집 컨텍스트)일 때만 true — 원본 key/url/transform 노출. 비소유자(공개 퀴즈 뷰어)에겐 크롭으로
   *     가린 원본을 노출하지 않는다.
   */
  public static QuestionResponse from(
      Question question,
      String imageUrl,
      String originalImageUrl,
      String answerImageUrl,
      String originalAnswerImageUrl,
      boolean includeOriginal) {
    return new QuestionResponse(
        question.getId(),
        question.getOrderNum(),
        question.getImageKey(),
        imageUrl,
        includeOriginal ? question.getOriginalImageKey() : null,
        includeOriginal ? originalImageUrl : null,
        includeOriginal ? question.getImageTransform() : null,
        question.getAnswerImageKey(),
        answerImageUrl,
        includeOriginal ? question.getOriginalAnswerImageKey() : null,
        includeOriginal ? originalAnswerImageUrl : null,
        includeOriginal ? question.getAnswerImageTransform() : null,
        question.getQuestionText(),
        question.getAnswer());
  }
}
