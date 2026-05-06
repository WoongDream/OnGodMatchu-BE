package com.ongodmatchu.domain.quiz.dto;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import java.time.LocalDateTime;
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
    QuizVisibility visibility,
    String authorNickname,
    LocalDateTime createdAt) {

  public static QuizResponse from(Quiz quiz, String thumbnailUrl) {
    return new QuizResponse(
        quiz.getId(),
        quiz.getPublicId(),
        quiz.getTitle(),
        quiz.getDescription(),
        quiz.getCategory(),
        quiz.getThumbnailKey(),
        thumbnailUrl,
        quiz.getPlayCount(),
        quiz.getVisibility(),
        quiz.getUser().getNickname(),
        quiz.getCreatedAt());
  }
}
