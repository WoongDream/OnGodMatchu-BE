package com.ongodmatchu.domain.admin.dto;

import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserResponse(
    UUID userId,
    String nickname,
    String email,
    @Schema(description = "프로필 이미지 presigned URL. 목록 조회에서만 채워짐", nullable = true)
        String profileImageUrl,
    String role,
    @Schema(description = "ACTIVE/SUSPENDED/WITHDRAWN") String status,
    @Schema(description = "정지 만료 시각. 정지 아니면 null", nullable = true) OffsetDateTime suspendedUntil,
    String provider,
    OffsetDateTime createdAt) {

  /** 변경 액션 응답용 — 프로필 이미지 URL 미포함 (호출부가 목록을 재조회). */
  public static AdminUserResponse from(User user) {
    return from(user, null);
  }

  public static AdminUserResponse from(User user, String profileImageUrl) {
    return new AdminUserResponse(
        user.getPublicId(),
        user.getNickname(),
        user.getEmail(),
        profileImageUrl,
        user.getRole().name(),
        user.getStatus().name(),
        TimeFormat.toResponse(user.getSuspendedUntil()),
        user.getProvider().name(),
        TimeFormat.toResponse(user.getCreatedAt()));
  }
}
