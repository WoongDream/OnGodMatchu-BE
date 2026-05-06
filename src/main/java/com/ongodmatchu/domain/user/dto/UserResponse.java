package com.ongodmatchu.domain.user.dto;

import com.ongodmatchu.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
    UUID userId,
    String nickname,
    String email,
    String profileImageUrl,
    String bio,
    LocalDateTime createdAt,
    long activeDays,
    boolean isProfilePublic,
    String provider) {

  public static UserResponse from(User user, String profileImageUrl, long activeDays) {
    return new UserResponse(
        user.getPublicId(),
        user.getNickname(),
        user.getEmail(),
        profileImageUrl,
        user.getBio(),
        user.getCreatedAt(),
        activeDays,
        user.isProfilePublic(),
        user.getProvider().name());
  }
}
