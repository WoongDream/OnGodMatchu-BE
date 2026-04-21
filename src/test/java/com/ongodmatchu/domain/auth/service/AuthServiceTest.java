package com.ongodmatchu.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.auth.dto.EmailVerifyRequest;
import com.ongodmatchu.domain.auth.dto.LoginRequest;
import com.ongodmatchu.domain.auth.dto.SignupRequest;
import com.ongodmatchu.domain.auth.dto.TokenResponse;
import com.ongodmatchu.domain.auth.entity.EmailVerification;
import com.ongodmatchu.domain.auth.entity.RefreshToken;
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
import org.mockito.ArgumentCaptor;
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

  // ============ Signup Tests ============

  @Test
  @DisplayName("회원가입_이메일중복_예외발생")
  void signup_duplicateEmail_throwsException() {
    given(userRepository.existsByEmail("dup@example.com")).willReturn(true);

    assertThatThrownBy(
            () -> authService.signup(new SignupRequest("dup@example.com", "닉네임", "password123")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

    then(userRepository).should(never()).save(any());
    then(emailVerificationRepository).should(never()).save(any());
    then(mailService).should(never()).sendVerificationCode(anyString(), anyString());
  }

  @Test
  @DisplayName("회원가입_정상_사용자저장_인증코드발송")
  void signup_success_savesUserAndSendsVerificationCode() {
    given(userRepository.existsByEmail("new@example.com")).willReturn(false);
    given(passwordEncoder.encode("password123")).willReturn("hashed-password");

    authService.signup(new SignupRequest("new@example.com", "새사용자", "password123"));

    then(userRepository).should().save(any(User.class));
    then(emailVerificationRepository).should().deleteByEmail("new@example.com");
    then(emailVerificationRepository).should().save(any(EmailVerification.class));
    then(mailService).should().sendVerificationCode(anyString(), anyString());
  }

  @Test
  @DisplayName("회원가입_사용자_속성_검증")
  void signup_success_userAttributesCorrect() {
    given(userRepository.existsByEmail("test@example.com")).willReturn(false);
    given(passwordEncoder.encode("password123")).willReturn("encoded-pass");

    authService.signup(new SignupRequest("test@example.com", "테스트", "password123"));

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    then(userRepository).should().save(userCaptor.capture());

    User savedUser = userCaptor.getValue();
    assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
    assertThat(savedUser.getNickname()).isEqualTo("테스트");
    assertThat(savedUser.getPassword()).isEqualTo("encoded-pass");
    assertThat(savedUser.getProvider()).isEqualTo(AuthProvider.LOCAL);
    assertThat(savedUser.isEmailVerified()).isFalse();
  }

  // ============ SendVerificationCode Tests ============

  @Test
  @DisplayName("인증코드발송_기존코드삭제_새코드저장")
  void sendVerificationCode_deleteExistingAndSaveNew() {
    authService.sendVerificationCode("test@example.com");

    then(emailVerificationRepository).should().deleteByEmail("test@example.com");
    then(emailVerificationRepository).should().save(any(EmailVerification.class));
    then(mailService).should().sendVerificationCode(anyString(), anyString());
  }

  @Test
  @DisplayName("인증코드발송_메일서비스호출")
  void sendVerificationCode_callsMailService() {
    authService.sendVerificationCode("user@example.com");

    then(mailService).should().sendVerificationCode(anyString(), anyString());
  }

  @Test
  @DisplayName("인증코드발송_코드형식_6자리숫자")
  void sendVerificationCode_codeFormat6Digits() {
    authService.sendVerificationCode("user@example.com");

    ArgumentCaptor<EmailVerification> verificationCaptor =
        ArgumentCaptor.forClass(EmailVerification.class);
    then(emailVerificationRepository).should().save(verificationCaptor.capture());

    EmailVerification savedVerification = verificationCaptor.getValue();
    assertThat(savedVerification.getCode()).matches("\\d{6}");
    assertThat(savedVerification.getCode()).hasSize(6);
  }

  // ============ VerifyEmail Tests ============

  @Test
  @DisplayName("이메일인증_코드미존재_예외발생")
  void verifyEmail_noVerificationRecord_throwsException() {
    given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("nonexistent@example.com"))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                authService.verifyEmail(
                    new EmailVerifyRequest("nonexistent@example.com", "123456")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);
  }

  @Test
  @DisplayName("이메일인증_코드만료_예외발생")
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
            () -> authService.verifyEmail(new EmailVerifyRequest("test@example.com", "123456")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED);
  }

  @Test
  @DisplayName("이메일인증_잘못된코드_예외발생")
  void verifyEmail_wrongCode_throwsException() {
    EmailVerification verification =
        EmailVerification.builder()
            .email("test@example.com")
            .code("123456")
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
    given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("test@example.com"))
        .willReturn(Optional.of(verification));

    assertThatThrownBy(
            () -> authService.verifyEmail(new EmailVerifyRequest("test@example.com", "999999")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);
  }

  @Test
  @DisplayName("이메일인증_사용자미존재_예외발생")
  void verifyEmail_userNotFound_throwsException() {
    EmailVerification verification =
        EmailVerification.builder()
            .email("notfound@example.com")
            .code("123456")
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
    given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("notfound@example.com"))
        .willReturn(Optional.of(verification));
    given(userRepository.findByEmail("notfound@example.com")).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> authService.verifyEmail(new EmailVerifyRequest("notfound@example.com", "123456")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("이메일인증_정상_사용자인증완료")
  void verifyEmail_success_userVerified() {
    EmailVerification verification =
        EmailVerification.builder()
            .email("verify@example.com")
            .code("123456")
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
    User user =
        User.builder()
            .email("verify@example.com")
            .nickname("사용자")
            .password("hashed")
            .provider(AuthProvider.LOCAL)
            .emailVerified(false)
            .build();

    given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("verify@example.com"))
        .willReturn(Optional.of(verification));
    given(userRepository.findByEmail("verify@example.com")).willReturn(Optional.of(user));

    authService.verifyEmail(new EmailVerifyRequest("verify@example.com", "123456"));

    assertThat(verification.isVerified()).isTrue();
    assertThat(user.isEmailVerified()).isTrue();
  }

  // ============ Login Tests ============

  @Test
  @DisplayName("로그인_사용자미존재_예외발생")
  void login_userNotFound_throwsException() {
    given(userRepository.findByEmail("notfound@example.com")).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> authService.login(new LoginRequest("notfound@example.com", "password123")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("로그인_소셜사용자_예외발생")
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
  @DisplayName("로그인_잘못된비밀번호_예외발생")
  void login_invalidPassword_throwsException() {
    User user =
        User.builder()
            .email("wrong@example.com")
            .nickname("사용자")
            .password("hashed-correct")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    given(userRepository.findByEmail("wrong@example.com")).willReturn(Optional.of(user));
    given(passwordEncoder.matches("wrong-password", "hashed-correct")).willReturn(false);

    assertThatThrownBy(
            () -> authService.login(new LoginRequest("wrong@example.com", "wrong-password")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_PASSWORD);
  }

  @Test
  @DisplayName("로그인_이메일미인증_예외발생")
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
  @DisplayName("로그인_정상_토큰반환")
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

  // ============ Refresh Tests ============

  @Test
  @DisplayName("토큰갱신_리프레시토큰미존재_예외발생")
  void refresh_tokenNotFound_throwsException() {
    given(refreshTokenRepository.findByToken("invalid-token")).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.refresh("invalid-token"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);

    then(refreshTokenRepository).should(never()).delete(any());
  }

  @Test
  @DisplayName("토큰갱신_토큰만료_삭제후예외발생")
  void refresh_expiredToken_deleteAndThrowException() {
    RefreshToken expiredToken =
        RefreshToken.builder()
            .userId(1L)
            .token("expired-token")
            .expiresAt(LocalDateTime.now().minusSeconds(1))
            .build();
    given(refreshTokenRepository.findByToken("expired-token"))
        .willReturn(Optional.of(expiredToken));

    assertThatThrownBy(() -> authService.refresh("expired-token"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TOKEN_EXPIRED);

    then(refreshTokenRepository).should().delete(expiredToken);
  }

  @Test
  @DisplayName("토큰갱신_정상_새토큰발급")
  void refresh_success_issuesNewTokens() {
    RefreshToken validToken =
        RefreshToken.builder()
            .userId(1L)
            .token("valid-token")
            .expiresAt(LocalDateTime.now().plusSeconds(3600))
            .build();
    ReflectionTestUtils.setField(authService, "refreshTokenExpiry", 1209600000L);

    given(refreshTokenRepository.findByToken("valid-token")).willReturn(Optional.of(validToken));
    given(jwtProvider.generateAccessToken(1L)).willReturn("new-access-token");
    given(jwtProvider.generateRefreshToken(1L)).willReturn("new-refresh-token");

    TokenResponse result = authService.refresh("valid-token");

    assertThat(result.accessToken()).isEqualTo("new-access-token");
    assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
    then(refreshTokenRepository).should().delete(validToken);
    then(refreshTokenRepository).should().save(any(RefreshToken.class));
  }

  // ============ Logout Tests ============

  @Test
  @DisplayName("로그아웃_사용자리프레시토큰삭제")
  void logout_deletesUserRefreshTokens() {
    authService.logout(1L);

    then(refreshTokenRepository).should().deleteByUserId(1L);
  }

  @Test
  @DisplayName("로그아웃_다른사용자토큰유지")
  void logout_onlyDeletesSpecificUserTokens() {
    authService.logout(2L);

    then(refreshTokenRepository).should().deleteByUserId(2L);
  }
}
