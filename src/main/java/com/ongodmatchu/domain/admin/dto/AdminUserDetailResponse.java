package com.ongodmatchu.domain.admin.dto;

import com.ongodmatchu.domain.quiz.repository.QuizRepository.QuizAggregateRow;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 유저 수정 화면용 상세. 프로필 + 역할/상태 + 플레이 기록 + 만든 퀴즈 통계 (관리자 시점이라 비공개 포함 전체 집계). */
public record AdminUserDetailResponse(
    UUID userId,
    String nickname,
    String email,
    String profileImageUrl,
    @Schema(nullable = true) String bio,
    String role,
    @Schema(description = "ACTIVE/SUSPENDED/WITHDRAWN") String status,
    @Schema(description = "정지 만료 시각. 정지 아니면 null", nullable = true) OffsetDateTime suspendedUntil,
    String provider,
    OffsetDateTime createdAt,
    @Schema(description = "탈퇴 시각. 탈퇴 유저만 값, 그 외 null", nullable = true) OffsetDateTime withdrawnAt,
    @Schema(description = "본인이 푼 횟수") long solvedCount,
    @Schema(description = "본인 평균 정답률(0~100). 시도 0이면 null", nullable = true) Double avgSolveRate,
    @Schema(description = "만든 퀴즈 총 개수") long quizCount,
    @Schema(description = "만든 퀴즈 누적 플레이") long totalPlayCount,
    @Schema(description = "만든 퀴즈 받은 스타") long totalStarCount) {

  public static AdminUserDetailResponse of(
      User user,
      String profileImageUrl,
      long solvedCount,
      Double avgSolveRate,
      QuizAggregateRow agg) {
    return new AdminUserDetailResponse(
        user.getPublicId(),
        user.getNickname(),
        user.getEmail(),
        profileImageUrl,
        user.getBio(),
        user.getRole().name(),
        user.getStatus().name(),
        TimeFormat.toResponse(user.getSuspendedUntil()),
        user.getProvider().name(),
        TimeFormat.toResponse(user.getCreatedAt()),
        TimeFormat.toResponse(user.getDeletedAt()),
        solvedCount,
        avgSolveRate,
        agg.getQuizCount(),
        agg.getPlays(),
        agg.getStars());
  }
}
