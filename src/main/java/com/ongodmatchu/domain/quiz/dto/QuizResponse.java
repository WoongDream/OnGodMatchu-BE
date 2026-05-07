package com.ongodmatchu.domain.quiz.dto;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QuizResponse(
    Long id,
    UUID publicId,
    String title,
    String description,
    String category,
    String thumbnailKey,
    @Schema(description = "썸네일 presigned GET URL — TTL 1시간, 응답마다 새 URL") String thumbnailUrl,
    int playCount,
    int starCount,
    int commentCount,
    int shareCount,
    @Schema(description = "현재 사용자가 좋아요(스타) 눌렀는지. 비로그인 = null (모름)", nullable = true)
        Boolean isStarred,
    QuizVisibility visibility,
    String authorNickname,
    @Schema(description = "생성 시각 (ISO 8601 +09:00)") OffsetDateTime createdAt) {

  public static QuizResponse from(Quiz quiz, String thumbnailUrl) {
    return from(quiz, thumbnailUrl, null);
  }

  public static QuizResponse from(Quiz quiz, String thumbnailUrl, Boolean isStarred) {
    return new QuizResponse(
        quiz.getId(),
        quiz.getPublicId(),
        quiz.getTitle(),
        quiz.getDescription(),
        quiz.getCategory(),
        quiz.getThumbnailKey(),
        thumbnailUrl,
        quiz.getPlayCount(),
        quiz.getStarCount(),
        quiz.getCommentCount(),
        quiz.getShareCount(),
        isStarred,
        quiz.getVisibility(),
        quiz.getUser().getNickname(),
        TimeFormat.toResponse(quiz.getCreatedAt()));
  }
}
