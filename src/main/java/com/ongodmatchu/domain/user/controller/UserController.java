package com.ongodmatchu.domain.user.controller;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.user.dto.UserResponse;
import com.ongodmatchu.domain.user.service.UserService;
import com.ongodmatchu.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> getMe(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UserResponse response = userService.getMe(userDetails.getUser().getId());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }
}
