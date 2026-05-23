package com.ongodmatchu.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.auth.dto.LoginRequest;
import com.ongodmatchu.domain.auth.dto.NicknameAvailabilityResponse;
import com.ongodmatchu.domain.auth.dto.SignupRequest;
import com.ongodmatchu.domain.auth.dto.SignupResponse;
import com.ongodmatchu.domain.auth.dto.TokenResponse;
import com.ongodmatchu.domain.auth.entity.EmailVerification;
import com.ongodmatchu.domain.auth.entity.RefreshToken;
import com.ongodmatchu.domain.auth.jwt.JwtProvider;
import com.ongodmatchu.domain.auth.ratelimit.VerificationCodeRateLimiter;
import com.ongodmatchu.domain.auth.repository.EmailVerificationRepository;
import com.ongodmatchu.domain.auth.repository.RefreshTokenRepository;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.domain.user.validation.NicknameNormalizer;
import com.ongodmatchu.domain.user.validation.NicknamePolicy;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.global.exception.RateLimitException;
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
import org.springframework.dao.DataIntegrityViolationException;
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
  @Mock private com.ongodmatchu.domain.auth.validation.PasswordValidator passwordValidator;
  @Mock private NicknameNormalizer nicknameNormalizer;
  @Mock private NicknamePolicy nicknamePolicy;
  @Mock private VerificationCodeRateLimiter rateLimiter;
  @Mock private com.ongodmatchu.domain.user.service.ProfileImageInitializer profileImageInitializer;
  @Mock private com.ongodmatchu.infra.s3.S3Service s3Service;

  private static EmailVerification validVerification(String email, String code) {
    return EmailVerification.builder()
        .email(email)
        .code(code)
        .expiresAt(LocalDateTime.now().plusMinutes(5))
        .build();
  }

  // ============ requestVerificationCode Tests ============

  @Test
  @DisplayName("코드발송_이미가입된이메일_EMAIL_ALREADY_EXISTS")
  void requestVerificationCode_alreadyRegistered_throws() {
    given(userRepository.existsByEmail("dup@example.com")).willReturn(true);

    assertThatThrownBy(() -> authService.requestVerificationCode("dup@example.com", "1.1.1.1"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

    then(rateLimiter).should(never()).check(anyString(), anyString(), anyString());
    then(emailVerificationRepository).should(never()).save(any());
    then(mailService).should(never()).sendVerificationCode(anyString(), anyString());
  }

  @Test
  @DisplayName("코드발송_rate_limit_초과시_RateLimitException_저장및메일미발생")
  void requestVerificationCode_rateLimited_throws() {
    given(userRepository.existsByEmail("ok@example.com")).willReturn(false);
    willThrow(new RateLimitException(42))
        .given(rateLimiter)
        .check("ok@example.com", "1.1.1.1", "signup");

    assertThatThrownBy(() -> authService.requestVerificationCode("ok@example.com", "1.1.1.1"))
        .isInstanceOf(RateLimitException.class)
        .extracting(e -> ((RateLimitException) e).getRetryAfterSeconds())
        .isEqualTo(42L);

    then(emailVerificationRepository).should(never()).save(any());
    then(mailService).should(never()).sendVerificationCode(anyString(), anyString());
  }

  @Test
  @DisplayName("코드발송_정상_기존행삭제_새행저장_메일발송")
  void requestVerificationCode_success_deletesOldSavesNewSendsMail() {
    given(userRepository.existsByEmail("new@example.com")).willReturn(false);

    authService.requestVerificationCode("new@example.com", "1.2.3.4");

    then(rateLimiter).should().check("new@example.com", "1.2.3.4", "signup");
    then(emailVerificationRepository).should().deleteByEmail("new@example.com");

    ArgumentCaptor<EmailVerification> captor = ArgumentCaptor.forClass(EmailVerification.class);
    then(emailVerificationRepository).should().save(captor.capture());
    EmailVerification saved = captor.getValue();
    assertThat(saved.getCode()).matches("\\d{6}");
    assertThat(saved.getEmail()).isEqualTo("new@example.com");

    then(mailService).should().sendVerificationCode(anyString(), anyString());
  }

  // ============ Signup Tests ============

  @Test
  @DisplayName("회원가입_이메일중복_예외발생")
  void signup_duplicateEmail_throwsException() {
    given(userRepository.existsByEmail("dup@example.com")).willReturn(true);

    assertThatThrownBy(
            () ->
                authService.signup(
                    new SignupRequest(
                        "dup@example.com", "닉네임", "password123", "123456", true, true, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

    then(userRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("회원가입_코드없음_INVALID_VERIFICATION_CODE")
  void signup_noVerificationRecord_throws() {
    given(userRepository.existsByEmail("new@example.com")).willReturn(false);
    given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("new@example.com"))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                authService.signup(
                    new SignupRequest(
                        "new@example.com", "닉네임", "password123", "123456", true, true, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);

    then(userRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("회원가입_코드만료_VERIFICATION_CODE_EXPIRED")
  void signup_expiredCode_throws() {
    given(userRepository.existsByEmail("new@example.com")).willReturn(false);
    EmailVerification expired =
        EmailVerification.builder()
            .email("new@example.com")
            .code("123456")
            .expiresAt(LocalDateTime.now().minusMinutes(1))
            .build();
    given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("new@example.com"))
        .willReturn(Optional.of(expired));

    assertThatThrownBy(
            () ->
                authService.signup(
                    new SignupRequest(
                        "new@example.com", "닉네임", "password123", "123456", true, true, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED);

    then(userRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("회원가입_코드불일치_INVALID_VERIFICATION_CODE")
  void signup_wrongCode_throws() {
    given(userRepository.existsByEmail("new@example.com")).willReturn(false);
    given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("new@example.com"))
        .willReturn(Optional.of(validVerification("new@example.com", "123456")));

    assertThatThrownBy(
            () ->
                authService.signup(
                    new SignupRequest(
                        "new@example.com", "닉네임", "password123", "999999", true, true, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);

    then(userRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("회원가입_정상_emailVerified_true_토큰발급_검증행소비")
  void signup_success_returnsTokensAndConsumesVerification() {
    ReflectionTestUtils.setField(authService, "refreshTokenExpiry", 1209600000L);

    given(userRepository.existsByEmail("new@example.com")).willReturn(false);
    given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("new@example.com"))
        .willReturn(Optional.of(validVerification("new@example.com", "123456")));
    given(nicknameNormalizer.normalize("새사용자")).willReturn("새사용자");
    given(userRepository.existsByNickname("새사용자")).willReturn(false);
    given(passwordEncoder.encode("password123")).willReturn("hashed");
    given(userRepository.saveAndFlush(any(User.class)))
        .willAnswer(
            inv -> {
              User u = inv.getArgument(0);
              ReflectionTestUtils.setField(u, "id", 7L);
              ReflectionTestUtils.setField(u, "publicId", java.util.UUID.randomUUID());
              return u;
            });
    given(jwtProvider.generateAccessToken(7L)).willReturn("AT");
    given(jwtProvider.generateRefreshToken(7L)).willReturn("RT");

    SignupResponse response =
        authService.signup(
            new SignupRequest(
                "new@example.com", "새사용자", "password123", "123456", true, true, false));

    assertThat(response.accessToken()).isEqualTo("AT");
    assertThat(response.refreshToken()).isEqualTo("RT");
    assertThat(response.user().email()).isEqualTo("new@example.com");
    assertThat(response.user().nickname()).isEqualTo("새사용자");
    assertThat(response.user().userId()).isNotNull();

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    then(userRepository).should().saveAndFlush(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.getProvider()).isEqualTo(AuthProvider.LOCAL);
    assertThat(saved.isEmailVerified()).isTrue();
    assertThat(saved.getPassword()).isEqualTo("hashed");
    assertThat(saved.getTermsVersion()).isEqualTo("1.0");
    assertThat(saved.getPrivacyVersion()).isEqualTo("1.0");
    assertThat(saved.isMarketingAgreed()).isFalse();
    assertThat(saved.getTermsAgreedAt()).isNotNull();

    then(emailVerificationRepository).should().deleteByEmail("new@example.com");
    then(refreshTokenRepository).should().save(any(RefreshToken.class));
  }

  @Test
  @DisplayName("회원가입_약관미동의_TERMS_AGREEMENT_REQUIRED_사전차단_저장미호출")
  void signup_termsNotAgreed_throws() {
    assertThatThrownBy(
            () ->
                authService.signup(
                    new SignupRequest(
                        "new@example.com", "닉네임", "password123", "123456", false, true, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TERMS_AGREEMENT_REQUIRED);

    then(userRepository).should(never()).existsByEmail(anyString());
    then(userRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("회원가입_개인정보처리방침미동의_TERMS_AGREEMENT_REQUIRED")
  void signup_privacyNotAgreed_throws() {
    assertThatThrownBy(
            () ->
                authService.signup(
                    new SignupRequest(
                        "new@example.com", "닉네임", "password123", "123456", true, false, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TERMS_AGREEMENT_REQUIRED);

    then(userRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("회원가입_마케팅동의true_marketingAgreed_true_로_저장")
  void signup_marketingTrue_recordsTrue() {
    ReflectionTestUtils.setField(authService, "refreshTokenExpiry", 1209600000L);

    given(userRepository.existsByEmail("mk@example.com")).willReturn(false);
    given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("mk@example.com"))
        .willReturn(Optional.of(validVerification("mk@example.com", "123456")));
    given(nicknameNormalizer.normalize("마케터")).willReturn("마케터");
    given(userRepository.existsByNickname("마케터")).willReturn(false);
    given(passwordEncoder.encode("password123")).willReturn("hashed");
    given(userRepository.saveAndFlush(any(User.class)))
        .willAnswer(
            inv -> {
              User u = inv.getArgument(0);
              ReflectionTestUtils.setField(u, "id", 8L);
              ReflectionTestUtils.setField(u, "publicId", java.util.UUID.randomUUID());
              return u;
            });
    given(jwtProvider.generateAccessToken(8L)).willReturn("AT");
    given(jwtProvider.generateRefreshToken(8L)).willReturn("RT");

    authService.signup(
        new SignupRequest("mk@example.com", "마케터", "password123", "123456", true, true, true));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    then(userRepository).should().saveAndFlush(captor.capture());
    assertThat(captor.getValue().isMarketingAgreed()).isTrue();
  }

  @Test
  @DisplayName("회원가입_닉네임중복_예외발생_saveAndFlush미호출")
  void signup_duplicateNickname_throws() {
    given(userRepository.existsByEmail("user@example.com")).willReturn(false);
    given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("user@example.com"))
        .willReturn(Optional.of(validVerification("user@example.com", "123456")));
    given(nicknameNormalizer.normalize("중복닉네임")).willReturn("중복닉네임");
    given(userRepository.existsByNickname("중복닉네임")).willReturn(true);

    assertThatThrownBy(
            () ->
                authService.signup(
                    new SignupRequest(
                        "user@example.com", "중복닉네임", "password123", "123456", true, true, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS);

    then(userRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("회원가입_레이스컨디션_DataIntegrityViolation_닉네임포함_NICKNAME_ALREADY_EXISTS")
  void signup_raceConditionNicknameDuplicate_converts() {
    given(userRepository.existsByEmail("race@example.com")).willReturn(false);
    given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("race@example.com"))
        .willReturn(Optional.of(validVerification("race@example.com", "123456")));
    given(nicknameNormalizer.normalize("레이스닉네임")).willReturn("레이스닉네임");
    given(userRepository.existsByNickname("레이스닉네임")).willReturn(false);
    given(passwordEncoder.encode("password123")).willReturn("hashed");

    RuntimeException cause =
        new RuntimeException("ERROR: duplicate key value violates unique constraint nickname");
    DataIntegrityViolationException dive =
        new DataIntegrityViolationException("constraint violation", cause);
    given(userRepository.saveAndFlush(any(User.class))).willThrow(dive);

    assertThatThrownBy(
            () ->
                authService.signup(
                    new SignupRequest(
                        "race@example.com", "레이스닉네임", "password123", "123456", true, true, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS);
  }

  @Test
  @DisplayName("회원가입_닉네임형식위반_NicknamePolicy예외_saveAndFlush미호출")
  void signup_invalidNicknameFormat_throws() {
    given(userRepository.existsByEmail("user@example.com")).willReturn(false);
    given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc("user@example.com"))
        .willReturn(Optional.of(validVerification("user@example.com", "123456")));
    given(nicknameNormalizer.normalize("x")).willReturn("x");
    willThrow(new BusinessException(ErrorCode.INVALID_NICKNAME_FORMAT))
        .given(nicknamePolicy)
        .enforce("x");

    assertThatThrownBy(
            () ->
                authService.signup(
                    new SignupRequest(
                        "user@example.com", "x", "password123", "123456", true, true, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_NICKNAME_FORMAT);

    then(userRepository).should(never()).saveAndFlush(any());
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
  @DisplayName("로그인_시스템계정_USER_NOT_FOUND마스킹")
  void login_systemAccount_throwsUserNotFound() {
    User systemUser =
        User.builder()
            .email("admin@system.local")
            .nickname("관리자")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(systemUser, "isSystem", true);
    given(userRepository.findByEmail("admin@system.local")).willReturn(Optional.of(systemUser));

    assertThatThrownBy(() -> authService.login(new LoginRequest("admin@system.local", "anything")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("로그인_탈퇴계정_USER_NOT_FOUND마스킹")
  void login_inactiveUser_throwsUserNotFound() {
    User withdrawn =
        User.builder()
            .email("deleted_abc@deleted.local")
            .nickname("deleted_abc")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(withdrawn, "isActive", false);
    given(userRepository.findByEmail("deleted_abc@deleted.local"))
        .willReturn(Optional.of(withdrawn));

    assertThatThrownBy(
            () -> authService.login(new LoginRequest("deleted_abc@deleted.local", "anything")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
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
    User activeUser =
        User.builder()
            .email("ok@example.com")
            .nickname("유저")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(activeUser, "id", 1L);
    ReflectionTestUtils.setField(authService, "refreshTokenExpiry", 1209600000L);

    given(refreshTokenRepository.findByToken("valid-token")).willReturn(Optional.of(validToken));
    given(userRepository.findById(1L)).willReturn(Optional.of(activeUser));
    given(jwtProvider.generateAccessToken(1L)).willReturn("new-access-token");
    given(jwtProvider.generateRefreshToken(1L)).willReturn("new-refresh-token");

    TokenResponse result = authService.refresh("valid-token");

    assertThat(result.accessToken()).isEqualTo("new-access-token");
    assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
    then(refreshTokenRepository).should().delete(validToken);
    then(refreshTokenRepository).should().save(any(RefreshToken.class));
  }

  @Test
  @DisplayName("토큰갱신_탈퇴계정_UNAUTHORIZED_RT삭제")
  void refresh_inactiveUser_throwsUnauthorized() {
    RefreshToken validToken =
        RefreshToken.builder()
            .userId(7L)
            .token("valid-token")
            .expiresAt(LocalDateTime.now().plusSeconds(3600))
            .build();
    User withdrawn =
        User.builder()
            .email("deleted_xyz@deleted.local")
            .nickname("deleted_xyz")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(withdrawn, "id", 7L);
    ReflectionTestUtils.setField(withdrawn, "isActive", false);

    given(refreshTokenRepository.findByToken("valid-token")).willReturn(Optional.of(validToken));
    given(userRepository.findById(7L)).willReturn(Optional.of(withdrawn));

    assertThatThrownBy(() -> authService.refresh("valid-token"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);

    then(refreshTokenRepository).should().delete(validToken);
    then(refreshTokenRepository).should(never()).save(any(RefreshToken.class));
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

  // ============ CheckNicknameAvailability Tests ============

  @Test
  @DisplayName("checkNicknameAvailability_형식위반_unavailable_format반환")
  void checkNicknameAvailability_invalidFormat_returnsUnavailableFormat() {
    given(nicknameNormalizer.normalize("x")).willReturn("x");
    given(nicknamePolicy.isValid("x")).willReturn(false);

    NicknameAvailabilityResponse result = authService.checkNicknameAvailability("x");

    assertThat(result.available()).isFalse();
    assertThat(result.reason()).isEqualTo(NicknameAvailabilityResponse.REASON_FORMAT);
    then(userRepository).should(never()).existsByNickname(anyString());
  }

  @Test
  @DisplayName("checkNicknameAvailability_닉네임중복_unavailable_duplicate반환")
  void checkNicknameAvailability_duplicateNickname_returnsUnavailableDuplicate() {
    given(nicknameNormalizer.normalize("이미있는닉네임")).willReturn("이미있는닉네임");
    given(nicknamePolicy.isValid("이미있는닉네임")).willReturn(true);
    given(userRepository.existsByNickname("이미있는닉네임")).willReturn(true);

    NicknameAvailabilityResponse result = authService.checkNicknameAvailability("이미있는닉네임");

    assertThat(result.available()).isFalse();
    assertThat(result.reason()).isEqualTo(NicknameAvailabilityResponse.REASON_DUPLICATE);
  }

  @Test
  @DisplayName("checkNicknameAvailability_사용가능닉네임_AVAILABLE반환")
  void checkNicknameAvailability_validAndUnique_returnsAvailable() {
    given(nicknameNormalizer.normalize("새닉네임")).willReturn("새닉네임");
    given(nicknamePolicy.isValid("새닉네임")).willReturn(true);
    given(userRepository.existsByNickname("새닉네임")).willReturn(false);

    NicknameAvailabilityResponse result = authService.checkNicknameAvailability("새닉네임");

    assertThat(result.available()).isTrue();
    assertThat(result.reason()).isNull();
  }
}
