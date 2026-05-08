package com.ongodmatchu.domain.quiz.dto;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizAttempt;
import com.ongodmatchu.domain.quiz.entity.QuizCategory;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 풀이 기록 리스트 아이템. 본인/외부 뷰어 동일 스키마. */
public record AttemptListItemResponse(
    @Schema(description = "attempt id") Long id,
    @Schema(description = "퀴즈 numeric ID") Long quizId,
    @Schema(description = "퀴즈 publicId UUID") UUID quizPublicId,
    String quizTitle,
    @Schema(description = "카테고리 영문 키") String quizCategory,
    @Schema(description = "카테고리 한국어 라벨") String quizCategoryLabel,
    String quizThumbnailKey,
    @Schema(description = "썸네일 presigned GET URL — TTL 1시간, 응답마다 새 URL") String quizThumbnailUrl,
    int score,
    int totalQuestions,
    @Schema(description = "정답률 0~100. totalQuestions=0 이면 null", nullable = true) Double percent,
    @Schema(description = "풀이 완료 시각 (ISO 8601 +09:00)") OffsetDateTime completedAt) {

  public static AttemptListItemResponse from(QuizAttempt attempt, String thumbnailUrl) {
    Quiz quiz = attempt.getQuiz();
    Double percent =
        attempt.getTotalQuestions() > 0
            ? (attempt.getScore() * 100.0 / attempt.getTotalQuestions())
            : null;
    return new AttemptListItemResponse(
        attempt.getId(),
        quiz.getId(),
        quiz.getPublicId(),
        quiz.getTitle(),
        quiz.getCategory(),
        QuizCategory.fromKey(quiz.getCategory()).map(QuizCategory::getLabel).orElse(null),
        quiz.getThumbnailKey(),
        thumbnailUrl,
        attempt.getScore(),
        attempt.getTotalQuestions(),
        percent,
        TimeFormat.toResponse(attempt.getCompletedAt()));
  }
}
