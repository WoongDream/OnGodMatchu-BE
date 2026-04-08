package com.ongodmatchu.domain.quiz.dto;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import java.time.LocalDateTime;

public record QuizResponse(
    Long id,
    String title,
    String description,
    String category,
    String thumbnailUrl,
    int playCount,
    String authorNickname,
    LocalDateTime createdAt) {

  public static QuizResponse from(Quiz quiz) {
    return new QuizResponse(
        quiz.getId(),
        quiz.getTitle(),
        quiz.getDescription(),
        quiz.getCategory(),
        quiz.getThumbnailUrl(),
        quiz.getPlayCount(),
        quiz.getUser().getNickname(),
        quiz.getCreatedAt());
  }
}
