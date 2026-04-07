package com.ongodmatchu.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.auth.dto.LoginRequest;
import com.ongodmatchu.domain.auth.dto.SignupRequest;
import com.ongodmatchu.domain.auth.dto.TokenResponse;
import com.ongodmatchu.domain.auth.entity.EmailVerification;
import com.ongodmatchu.domain.auth.jwt.JwtProvider;
import com.ongodmatchu.domain.auth.repository.EmailVerificationRepository;
import com.ongodmatchu.domain.auth.repository.RefreshTokenRepository;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.mail.MailService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @InjectMocks private AuthService authService;
  @Mock private UserRepository userRepository;
  @Mock private EmailVerificationRepository emailVerificationRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private JwtProvider jwtProvider;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private MailService mailService;

  @Test
  @DisplayName("이메일 중복 시 회원가입 실패")
  void signup_duplicateEmail_throwsException() {
    given(userRepository.existsByEmail("dup@example.com")).willReturn(true);

    assertThatThrownBy(
            () -> authService.signup(new SignupRequest("dup@example.com", "닉네임", "password123")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

    then(userRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("소셜 로그인 유저는 비밀번호 로그인 불가")
  void login_socialUser_throwsException() {
    User socialUser =
        User.builder()
            .email("social@example.com")
            .nickname("소셜유저")
            .provider(AuthProvider.GOOGLE)
            .emailVerified(true)
            .build();
    given(userRepository.findByEmail("social@example.com")).willReturn(Optional.of(socialUser));

    assertThatThrownBy(
            () -> authService.login(new LoginRequest("social@example.com", "password123")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.SOCIAL_USER_PASSWORD_LOGIN);
  }

  @Test
  @DisplayName("이메일 미인증 유저는 로그인 불가")
  void login_emailNotVerified_throwsException() {
    User unverifiedUser =
        User.builder()
            .email("unverified@example.com")
            .nickname("미인증유저")
            .password("hashed")
            .provider(AuthProvider.LOCAL)
            .emailVerified(false)
            .build();
    given(userRepository.findByEmail("unverified@example.com"))
        .willReturn(Optional.of(unverifiedUser));
    given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

    assertThatThrownBy(
            () -> authService.login(new LoginRequest("unverified@example.com", "password123")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
  }

  @Test
  @DisplayName("정상 로그인 시 토큰 반환")
  void login_success_returnsTokens() {
    User user =
        User.builder()
            .email("ok@example.com")
            .nickname("유저")
            .password("hashed")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    ReflectionTestUtils.setField(authService, "refreshTokenExpiry", 1209600000L);

    given(userRepository.findByEmail("ok@example.com")).willReturn(Optional.of(user));
    given(passwordEncoder.matches("password123", "hashed")).willReturn(true);
    given(jwtProvider.generateAccessToken(1L)).willReturn("access-token");
    given(jwtProvider.generateRefreshToken(1L)).willReturn("refresh-token");

    TokenResponse result = authService.login(new LoginRequest("ok@example.com", "password123"));

    assertThat(result.accessToken()).isEqualTo("access-token");
    assertThat(result.refreshToken()).isEqualTo("refresh-token");
  }

  @Test
  @DisplayName("만료된 인증 코드로 이메일 인증 실패")
  void verifyEmail_expiredCode_throwsException() {
    EmailVerification expired =
        EmailVerification.builder()
            .email("test@example.com")
            .code("123456")
            .expiresAt(LocalDateTime.now().minusMinutes(1))
            .build();
    given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("test@example.com"))
        .willReturn(Optional.of(expired));

    assertThatThrownBy(
            () ->
                authService.verifyEmail(
                    new com.ongodmatchu.domain.auth.dto.EmailVerifyRequest(
                        "test@example.com", "123456")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED);
  }
}
