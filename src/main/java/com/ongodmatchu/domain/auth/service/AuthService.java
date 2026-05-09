package com.ongodmatchu.domain.auth.service;

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
import com.ongodmatchu.domain.auth.validation.PasswordValidator;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.domain.user.service.ProfileImageInitializer;
import com.ongodmatchu.domain.user.validation.NicknameNormalizer;
import com.ongodmatchu.domain.user.validation.NicknamePolicy;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.mail.MailService;
import com.ongodmatchu.infra.s3.S3Service;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private static final int VERIFICATION_EXPIRY_MINUTES = 5;

  private final UserRepository userRepository;
  private final EmailVerificationRepository emailVerificationRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProvider jwtProvider;
  private final PasswordEncoder passwordEncoder;
  private final MailService mailService;
  private final PasswordValidator passwordValidator;
  private final NicknameNormalizer nicknameNormalizer;
  private final NicknamePolicy nicknamePolicy;
  private final VerificationCodeRateLimiter rateLimiter;
  private final ProfileImageInitializer profileImageInitializer;
  private final S3Service s3Service;

  @Value("${jwt.refresh-token-expiry}")
  private long refreshTokenExpiry;

  @Value("${app.profile.default-image-url}")
  private String defaultProfileImageUrl;

  @Transactional
  public void requestVerificationCode(String email, String ipAddress) {
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    rateLimiter.check(email, ipAddress, "signup");

    String code = generateCode();
    emailVerificationRepository.deleteByEmail(email);
    emailVerificationRepository.save(
        EmailVerification.builder()
            .email(email)
            .code(code)
            .expiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_EXPIRY_MINUTES))
            .build());
    mailService.sendVerificationCode(email, code);
  }

  @Transactional
  public SignupResponse signup(SignupRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    EmailVerification verification =
        emailVerificationRepository
            .findTopByEmailOrderByCreatedAtDesc(request.email())
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE));
    if (verification.isExpired()) {
      throw new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED);
    }
    if (!verification.getCode().equals(request.code())) {
      throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
    }

    String nickname = nicknameNormalizer.normalize(request.nickname());
    nicknamePolicy.enforce(nickname);
    if (userRepository.existsByNickname(nickname)) {
      throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
    }

    passwordValidator.validate(request.password(), request.email(), nickname);

    User user =
        User.builder()
            .email(request.email())
            .nickname(nickname)
            .password(passwordEncoder.encode(request.password()))
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      String message = e.getMostSpecificCause().getMessage();
      if (message != null && message.toLowerCase().contains("nickname")) {
        throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
      }
      throw e;
    }

    emailVerificationRepository.deleteByEmail(request.email());

    profileImageInitializer.initialize(user);

    String profileImageUrl =
        user.getProfileImageKey() == null
            ? defaultProfileImageUrl
            : s3Service.generateViewUrl(user.getProfileImageKey()).viewUrl();
    return SignupResponse.of(user, issueTokens(user.getId()), profileImageUrl);
  }

  @Transactional(readOnly = true)
  public NicknameAvailabilityResponse checkNicknameAvailability(String rawNickname) {
    String nickname = nicknameNormalizer.normalize(rawNickname);
    if (!nicknamePolicy.isValid(nickname)) {
      return NicknameAvailabilityResponse.unavailable(NicknameAvailabilityResponse.REASON_FORMAT);
    }
    if (userRepository.existsByNickname(nickname)) {
      return NicknameAvailabilityResponse.unavailable(
          NicknameAvailabilityResponse.REASON_DUPLICATE);
    }
    return NicknameAvailabilityResponse.AVAILABLE;
  }

  @Transactional
  public TokenResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    if (user.isSystem()) {
      // 시스템 계정은 로그인 차단 — 존재 자체를 노출하지 않도록 USER_NOT_FOUND 로 매핑.
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    if (user.getProvider() != AuthProvider.LOCAL) {
      throw new BusinessException(ErrorCode.SOCIAL_USER_PASSWORD_LOGIN);
    }
    if (user.getPassword() == null
        || !passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_PASSWORD);
    }
    if (!user.isEmailVerified()) {
      throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    return issueTokens(user.getId());
  }

  @Transactional
  public TokenResponse refresh(String refreshTokenValue) {
    RefreshToken refreshToken =
        refreshTokenRepository
            .findByToken(refreshTokenValue)
            .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

    if (refreshToken.isExpired()) {
      refreshTokenRepository.delete(refreshToken);
      throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
    }

    refreshTokenRepository.delete(refreshToken);
    return issueTokens(refreshToken.getUserId());
  }

  @Transactional
  public void logout(Long userId) {
    refreshTokenRepository.deleteByUserId(userId);
  }

  @Transactional
  public TokenResponse issueTokens(Long userId) {
    String accessToken = jwtProvider.generateAccessToken(userId);
    String refreshTokenValue = jwtProvider.generateRefreshToken(userId);

    refreshTokenRepository.deleteByUserId(userId);
    refreshTokenRepository.save(
        RefreshToken.builder()
            .userId(userId)
            .token(refreshTokenValue)
            .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiry / 1000))
            .build());

    return new TokenResponse(accessToken, refreshTokenValue);
  }

  private String generateCode() {
    SecureRandom random = new SecureRandom();
    int code = 100000 + random.nextInt(900000);
    return String.valueOf(code);
  }
}
