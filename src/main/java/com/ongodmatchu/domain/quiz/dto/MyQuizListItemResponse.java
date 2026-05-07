package com.ongodmatchu.domain.quiz.dto;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizCategory;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 프로필 (내가 만든 퀴즈 / 외부 뷰어용) 리스트 아이템. {@code correctRate} 는 풀이 기록 머지 후 채워질 자리이며 1차에서는 항상 null. */
public record MyQuizListItemResponse(
    UUID quizId,
    String title,
    @Schema(description = "카테고리 영문 키 (game/movie/...)") String category,
    @Schema(description = "카테고리 한국어 라벨 (게임/영화/...)") String categoryLabel,
    QuizVisibility visibility,
    String thumbnailKey,
    @Schema(description = "썸네일 presigned GET URL — TTL 1시간, 응답마다 새 URL") String thumbnailUrl,
    int playCount,
    int shareCount,
    int starCount,
    int commentCount,
    @Schema(
            description =
                "평균 정답률 (0~100). 풀이 기록 0회면 null (산출 불가). 1차에서는 항상 null — 풀이 기록 묶음 머지 후 채움",
            nullable = true)
        Double correctRate,
    @Schema(description = "생성 시각 (ISO 8601 +09:00)") OffsetDateTime createdAt,
    @Schema(description = "마지막 저장 시각 (ISO 8601 +09:00)") OffsetDateTime updatedAt) {

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
