package com.ongodmatchu.domain.quiz.dto;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.global.util.TimeFormat;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record QuizDetailResponse(
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
    OffsetDateTime createdAt,
    List<QuestionResponse> questions) {

  public static QuizDetailResponse of(
      Quiz quiz, String thumbnailUrl, Boolean isStarred, List<QuestionResponse> questions) {
    return new QuizDetailResponse(
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
        TimeFormat.toResponse(quiz.getCreatedAt()),
        questions);
  }

  public static QuizDetailResponse of(
      Quiz quiz, String thumbnailUrl, List<QuestionResponse> questions) {
    return of(quiz, thumbnailUrl, null, questions);
  }
}
