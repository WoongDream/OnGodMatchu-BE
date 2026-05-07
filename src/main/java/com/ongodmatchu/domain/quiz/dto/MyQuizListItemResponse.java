package com.ongodmatchu.domain.quiz.dto;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizCategory;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.global.util.TimeFormat;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 프로필 (내가 만든 퀴즈 / 외부 뷰어용) 리스트 아이템. {@code correctRate} 는 풀이 기록 머지 후 채워질 자리이며 1차에서는 항상 null. */
public record MyQuizListItemResponse(
    UUID quizId,
    String title,
    String category,
    String categoryLabel,
    QuizVisibility visibility,
    String thumbnailKey,
    String thumbnailUrl,
    int playCount,
    int shareCount,
    int starCount,
    int commentCount,
    Double correctRate,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static MyQuizListItemResponse from(Quiz quiz, String thumbnailUrl) {
    return new MyQuizListItemResponse(
        quiz.getPublicId(),
        quiz.getTitle(),
        quiz.getCategory(),
        QuizCategory.fromKey(quiz.getCategory()).map(QuizCategory::getLabel).orElse(null),
        quiz.getVisibility(),
        quiz.getThumbnailKey(),
        thumbnailUrl,
        quiz.getPlayCount(),
        quiz.getShareCount(),
        quiz.getStarCount(),
        quiz.getCommentCount(),
        null,
        TimeFormat.toResponse(quiz.getCreatedAt()),
        TimeFormat.toResponse(quiz.getUpdatedAt()));
  }
}
