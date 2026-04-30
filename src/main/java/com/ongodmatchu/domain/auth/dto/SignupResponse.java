package com.ongodmatchu.domain.auth.dto;

import com.ongodmatchu.domain.user.dto.UserResponse;
import com.ongodmatchu.domain.user.entity.User;

public record SignupResponse(UserResponse user, String accessToken, String refreshToken) {

  public static SignupResponse of(User user, TokenResponse tokens) {
    return new SignupResponse(UserResponse.from(user), tokens.accessToken(), tokens.refreshToken());
  }
}
