package com.ongodmatchu.domain.quiz.dto;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import java.time.LocalDateTime;
import java.util.List;

public record QuizDetailResponse(
    Long id,
    String title,
    String description,
    String category,
    String thumbnailUrl,
    int playCount,
    String authorNickname,
    LocalDateTime createdAt,
    List<QuestionResponse> questions) {

  public static QuizDetailResponse of(Quiz quiz, List<QuestionResponse> questions) {
    return new QuizDetailResponse(
        quiz.getId(),
        quiz.getTitle(),
        quiz.getDescription(),
        quiz.getCategory(),
        quiz.getThumbnailUrl(),
        quiz.getPlayCount(),
        quiz.getUser().getNickname(),
        quiz.getCreatedAt(),
        questions);
  }
}
