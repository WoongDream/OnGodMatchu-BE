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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Auth", description = "회원가입 / 로그인 / 토큰 — 자세한 흐름은 docs/api-development.md#86-인증--토큰")
public class AuthController {

  private final AuthService authService;

  @Operation(
      summary = "이메일 인증 코드 발송",
      description =
          "이메일별 60s 쿨다운 / 1h 5회, IP별 1h 10회. 가능 에러: EMAIL_ALREADY_EXISTS(409), RATE_LIMITED(429, error.retryAfter 초)")
  @PostMapping("/send-verification-code")
  public ResponseEntity<ApiResponse<Void>> sendVerificationCode(
      @Valid @RequestBody SendVerificationCodeRequest request, HttpServletRequest httpRequest) {
    authService.requestVerificationCode(request.email(), httpRequest.getRemoteAddr());
    return ResponseEntity.ok(ApiResponse.ok());
  }

  @Operation(
      summary = "회원가입",
      description =
          "이메일 인증 코드 검증 + 즉시 토큰 발급 (응답 201). 가능 에러: INVALID_VERIFICATION_CODE / VERIFICATION_CODE_EXPIRED / PASSWORD_POLICY_VIOLATION(400), EMAIL/NICKNAME_ALREADY_EXISTS(409), PASSWORD_BREACHED(422)")
  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<SignupResponse>> signup(
      @Valid @RequestBody SignupRequest request) {
    SignupResponse response = authService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  @Operation(
      summary = "닉네임 사용 가능 여부 확인",
      description = "debounce 검증용. `available=false` 면 `reason: format|duplicate`")
  @GetMapping("/check-nickname")
  public ResponseEntity<ApiResponse<NicknameAvailabilityResponse>> checkNickname(
      @RequestParam String nickname) {
    return ResponseEntity.ok(ApiResponse.ok(authService.checkNicknameAvailability(nickname)));
  }

  @Operation(
      summary = "로그인",
      description =
          "LOCAL 계정 로그인. OAuth 는 `/oauth2/...` 별도 흐름. 가능 에러: INVALID_PASSWORD(401), EMAIL_NOT_VERIFIED(403), SOCIAL_USER_PASSWORD_LOGIN(400)")
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<TokenResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    TokenResponse tokens = authService.login(request);
    return ResponseEntity.ok(ApiResponse.ok(tokens));
  }

  @Operation(
      summary = "토큰 갱신",
      description =
          "refreshToken 으로 새 access+refresh 한 쌍 발급. 401 응답 4종 (UNAUTHORIZED/INVALID_TOKEN/TOKEN_EXPIRED/REFRESH_TOKEN_NOT_FOUND) 모두 재로그인 처리 권장")
  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<TokenResponse>> refresh(@RequestBody String refreshToken) {
    TokenResponse tokens = authService.refresh(refreshToken);
    return ResponseEntity.ok(ApiResponse.ok(tokens));
  }

  @Operation(summary = "로그아웃", description = "현재 사용자의 refresh 토큰 무효화")
  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    authService.logout(userDetails.getUser().getId());
    return ResponseEntity.ok(ApiResponse.ok());
  }
}
