package com.ongodmatchu.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.ongodmatchu.domain.auth.validation.TermsPolicy;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
    UUID userId,
    String nickname,
    String email,
    String profileImageUrl,
    @Schema(description = "프로필 이미지 크롭 전 원본 key. 소유자에게만, 재편집 시 재전송용", nullable = true)
        String originalProfileImageKey,
    @Schema(
            description = "프로필 이미지 크롭 전 원본 presigned URL. 소유자에게만. 원본 미보존(자동 생성/레거시)이면 null",
            nullable = true)
        String originalProfileImageUrl,
    @JsonRawValue @Schema(description = "프로필 이미지 크롭/변환 파라미터 (opaque JSON). 소유자에게만", nullable = true)
        String profileImageTransform,
    String bio,
    OffsetDateTime createdAt,
    long activeDays,
    boolean isProfilePublic,
    String provider,
    boolean needsTermsAgreement,
    @Schema(description = "사용자 역할 — USER/ADMIN/OWNER") String role,
    @Schema(description = "파생 상태 — ACTIVE/SUSPENDED/WITHDRAWN") String status,
    @Schema(description = "정지 만료 시각. 정지 아니면 null", nullable = true) OffsetDateTime suspendedUntil) {

  /** 외부 뷰어/가입 직후 등 비-소유자 컨텍스트 — 원본/transform 미노출 (크롭으로 가린 영역 보호). */
  public static UserResponse from(User user, String profileImageUrl, long activeDays) {
    return build(user, profileImageUrl, null, null, null, activeDays);
  }

  /** 소유자 컨텍스트 — 재편집을 위해 원본 key/URL + transform 노출. */
  public static UserResponse from(
      User user, String profileImageUrl, String originalProfileImageUrl, long activeDays) {
    return build(
        user,
        profileImageUrl,
        user.getOriginalProfileImageKey(),
        originalProfileImageUrl,
        user.getProfileImageTransform(),
        activeDays);
  }

  private static UserResponse build(
      User user,
      String profileImageUrl,
      String originalProfileImageKey,
      String originalProfileImageUrl,
      String profileImageTransform,
      long activeDays) {
    return new UserResponse(
        user.getPublicId(),
        user.getNickname(),
        user.getEmail(),
        profileImageUrl,
        originalProfileImageKey,
        originalProfileImageUrl,
        profileImageTransform,
        user.getBio(),
        TimeFormat.toResponse(user.getCreatedAt()),
        activeDays,
        user.isProfilePublic(),
        user.getProvider().name(),
        TermsPolicy.needsAgreement(user.getTermsVersion(), user.getPrivacyVersion()),
        user.getRole().name(),
        user.getStatus().name(),
        TimeFormat.toResponse(user.getSuspendedUntil()));
  }
}
