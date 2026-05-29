package com.ongodmatchu.domain.quiz.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
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
    @Schema(description = "썸네일 크롭 전 원본 key. 소유자(편집)에게만, 재편집 시 재전송용", nullable = true)
        String originalThumbnailKey,
    @Schema(description = "썸네일 크롭 전 원본 presigned URL. 소유자에게만. 원본 미보존이면 null", nullable = true)
        String originalThumbnailUrl,
    @JsonRawValue @Schema(description = "썸네일 크롭/변환 파라미터 (opaque JSON). 소유자에게만", nullable = true)
        String thumbnailTransform,
    int playCount,
    int starCount,
    int commentCount,
    int shareCount,
    @Schema(description = "현재 사용자가 좋아요(스타) 눌렀는지. 비로그인 = null", nullable = true) Boolean isStarred,
    @Schema(description = "평균 정답률 (0~100). 풀이 기록 0회면 null", nullable = true) Double correctRate,
    QuizVisibility visibility,
    String authorNickname,
    @Schema(description = "작성자 프로필 이미지 presigned URL. 작성자 현재 key 로 매번 presign", nullable = true)
        String authorProfileImageUrl,
    @Schema(description = "생성 시각 (ISO 8601 +09:00)") OffsetDateTime createdAt,
    List<QuestionResponse> questions) {

  /**
   * @param includeOriginal 소유자(편집)일 때만 true — 썸네일 원본 key/url/transform 노출. 비소유자(공개 퀴즈 뷰어)에겐 크롭으로 가린
   *     원본을 노출하지 않는다.
   */
  public static QuizDetailResponse of(
      Quiz quiz,
      String thumbnailUrl,
      String originalThumbnailUrl,
      boolean includeOriginal,
      Boolean isStarred,
      Double correctRate,
      String authorProfileImageUrl,
      List<QuestionResponse> questions) {
    return new QuizDetailResponse(
        quiz.getId(),
        quiz.getPublicId(),
        quiz.getTitle(),
        quiz.getDescription(),
        quiz.getCategory(),
        quiz.getThumbnailKey(),
        thumbnailUrl,
        includeOriginal ? quiz.getOriginalThumbnailKey() : null,
        includeOriginal ? originalThumbnailUrl : null,
        includeOriginal ? quiz.getThumbnailTransform() : null,
        quiz.getPlayCount(),
        quiz.getStarCount(),
        quiz.getCommentCount(),
        quiz.getShareCount(),
        isStarred,
        correctRate,
        quiz.getVisibility(),
        quiz.getUser().getNickname(),
        authorProfileImageUrl,
        TimeFormat.toResponse(quiz.getCreatedAt()),
        questions);
  }

  public static QuizDetailResponse of(
      Quiz quiz, String thumbnailUrl, Boolean isStarred, List<QuestionResponse> questions) {
    return of(quiz, thumbnailUrl, null, false, isStarred, null, null, questions);
  }

  public static QuizDetailResponse of(
      Quiz quiz, String thumbnailUrl, List<QuestionResponse> questions) {
    return of(quiz, thumbnailUrl, null, false, null, null, null, questions);
  }
}
