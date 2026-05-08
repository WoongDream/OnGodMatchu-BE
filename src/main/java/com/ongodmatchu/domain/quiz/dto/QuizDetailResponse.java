package com.ongodmatchu.domain.quiz.dto;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "썸네일 presigned GET URL — TTL 1시간") String thumbnailUrl,
    int playCount,
    int starCount,
    int commentCount,
    int shareCount,
    @Schema(description = "현재 사용자가 좋아요(스타) 눌렀는지. 비로그인 = null", nullable = true) Boolean isStarred,
    @Schema(description = "평균 정답률 (0~100). 풀이 기록 0회면 null", nullable = true) Double correctRate,
    QuizVisibility visibility,
    String authorNickname,
    @Schema(description = "생성 시각 (ISO 8601 +09:00)") OffsetDateTime createdAt,
    List<QuestionResponse> questions) {

  public static QuizDetailResponse of(
      Quiz quiz,
      String thumbnailUrl,
      Boolean isStarred,
      Double correctRate,
      List<QuestionResponse> questions) {
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
        correctRate,
        quiz.getVisibility(),
        quiz.getUser().getNickname(),
        TimeFormat.toResponse(quiz.getCreatedAt()),
        questions);
  }

  public static QuizDetailResponse of(
      Quiz quiz, String thumbnailUrl, Boolean isStarred, List<QuestionResponse> questions) {
    return of(quiz, thumbnailUrl, isStarred, null, questions);
  }

  public static QuizDetailResponse of(
      Quiz quiz, String thumbnailUrl, List<QuestionResponse> questions) {
    return of(quiz, thumbnailUrl, null, null, questions);
  }
}
