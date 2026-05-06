package com.ongodmatchu.domain.user.dto;

import com.ongodmatchu.domain.user.entity.User;
import java.util.UUID;

/** 비공개 프로필 조회 시 외부 뷰어에게 노출되는 축약 응답. */
public record PublicUserResponse(
    UUID userId, String nickname, String profileImageUrl, boolean isProfilePublic) {

  public static PublicUserResponse from(User user, String profileImageUrl) {
    return new PublicUserResponse(user.getPublicId(), user.getNickname(), profileImageUrl, false);
  }
}
