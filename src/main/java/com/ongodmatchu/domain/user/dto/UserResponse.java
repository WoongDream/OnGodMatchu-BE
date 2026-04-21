package com.ongodmatchu.domain.user.dto;

import com.ongodmatchu.domain.user.entity.User;

public record UserResponse(Long id, String email, String nickname, String provider) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(), user.getEmail(), user.getNickname(), user.getProvider().name());
  }
}
