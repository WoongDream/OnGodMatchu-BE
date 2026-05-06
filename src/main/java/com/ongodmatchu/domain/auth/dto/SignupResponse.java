package com.ongodmatchu.domain.auth.dto;

import com.ongodmatchu.domain.user.dto.UserResponse;
import com.ongodmatchu.domain.user.entity.User;

public record SignupResponse(UserResponse user, String accessToken, String refreshToken) {

  public static SignupResponse of(User user, TokenResponse tokens, String defaultProfileImageUrl) {
    return new SignupResponse(
        UserResponse.from(user, defaultProfileImageUrl, 0L),
        tokens.accessToken(),
        tokens.refreshToken());
  }
}
