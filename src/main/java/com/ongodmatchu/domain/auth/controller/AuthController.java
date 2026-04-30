package com.ongodmatchu.domain.auth.controller;

import com.ongodmatchu.domain.auth.dto.LoginRequest;
import com.ongodmatchu.domain.auth.dto.NicknameAvailabilityResponse;
import com.ongodmatchu.domain.auth.dto.SendVerificationCodeRequest;
import com.ongodmatchu.domain.auth.dto.SignupRequest;
import com.ongodmatchu.domain.auth.dto.SignupResponse;
import com.ongodmatchu.domain.auth.dto.TokenResponse;
import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.auth.service.AuthService;
import com.ongodmatchu.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/send-verification-code")
  public ResponseEntity<ApiResponse<Void>> sendVerificationCode(
      @Valid @RequestBody SendVerificationCodeRequest request, HttpServletRequest httpRequest) {
    authService.requestVerificationCode(request.email(), httpRequest.getRemoteAddr());
    return ResponseEntity.ok(ApiResponse.ok());
  }

  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<SignupResponse>> signup(
      @Valid @RequestBody SignupRequest request) {
    SignupResponse response = authService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  @GetMapping("/check-nickname")
  public ResponseEntity<ApiResponse<NicknameAvailabilityResponse>> checkNickname(
      @RequestParam String nickname) {
    return ResponseEntity.ok(ApiResponse.ok(authService.checkNicknameAvailability(nickname)));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<TokenResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    TokenResponse tokens = authService.login(request);
    return ResponseEntity.ok(ApiResponse.ok(tokens));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<TokenResponse>> refresh(@RequestBody String refreshToken) {
    TokenResponse tokens = authService.refresh(refreshToken);
    return ResponseEntity.ok(ApiResponse.ok(tokens));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    authService.logout(userDetails.getUser().getId());
    return ResponseEntity.ok(ApiResponse.ok());
  }
}
