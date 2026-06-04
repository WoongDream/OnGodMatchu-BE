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
    String role,
    @Schema(description = "ACTIVE/SUSPENDED/WITHDRAWN") String status,
    @Schema(description = "정지 만료 시각. 정지 아니면 null", nullable = true) OffsetDateTime suspendedUntil,
    String provider,
    OffsetDateTime createdAt) {

  public static AdminUserResponse from(User user) {
    return new AdminUserResponse(
        user.getPublicId(),
        user.getNickname(),
        user.getEmail(),
        user.getRole().name(),
        user.getStatus().name(),
        TimeFormat.toResponse(user.getSuspendedUntil()),
        user.getProvider().name(),
        TimeFormat.toResponse(user.getCreatedAt()));
  }
}
