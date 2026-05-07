package com.ongodmatchu.domain.quiz.dto;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.global.util.TimeFormat;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QuizResponse(
    Long id,
    UUID publicId,
    String title,
    String description,
    String category,
    String thumbnailKey,
    String thumbnailUrl,
    int playCount,
    int starCount,
    int commentCount,
    int shareCount,
    Boolean isStarred,
    QuizVisibility visibility,
    String authorNickname,
    OffsetDateTime createdAt) {

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
