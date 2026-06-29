package com.ongodmatchu.domain.user.service;

import com.ongodmatchu.domain.auth.ratelimit.VerificationCodeRateLimiter;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.entity.WithdrawalVerification;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.domain.user.repository.WithdrawalVerificationRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.mail.MailService;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원탈퇴 본인 인증 코드 발송·검증. 가입 코드 흐름과는 분리 운영 (rate limit purpose / 저장 테이블 모두 별개). */
@Service
@RequiredArgsConstructor
public class WithdrawalCodeService {

  private static final int CODE_EXPIRY_MINUTES = 5;
  private static final String PURPOSE = "withdrawal";

  private final UserRepository userRepository;
  private final MailService mailService;
  private final VerificationCodeRateLimiter rateLimiter;
  private final WithdrawalVerificationRepository repository;

  /** 인증된 본인 이메일로 6자리 코드 발송. 발송마다 기존 미소비 코드는 폐기. */
  @Transactional
  public void sendCode(Long userId, String ipAddress) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    rateLimiter.check(user.getEmail(), ipAddress, PURPOSE);

    String code = generateCode();
    repository.deleteByUserId(userId);
    repository.save(
        WithdrawalVerification.builder()
            .userId(userId)
            .code(code)
            .expiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES))
            .build());
    mailService.sendWithdrawalCode(user.getEmail(), code);
  }

  /**
   * 코드 dry-run 검증. 유효하면 통과만 하고 소비하지 않는다 (탈퇴 확정 전 인증 버튼용). 최종 소비는 {@link #verifyAndConsume} 가 담당. 검증
   * 직전 시도 횟수 rate limit 으로 6자리 코드 brute-force 를 차단 (초과 시 {@link ErrorCode#RATE_LIMITED}). 미존재/미일치 시
   * {@link ErrorCode#INVALID_VERIFICATION_CODE}, 만료 시 {@link ErrorCode#VERIFICATION_CODE_EXPIRED}.
   */
  @Transactional(readOnly = true)
  public void verify(Long userId, String code, String ipAddress) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    rateLimiter.checkVerifyAttempt(user.getEmail(), ipAddress, PURPOSE);
    findValidOrThrow(userId, code);
  }

  /**
   * 코드 검증 + 즉시 소비. 미존재/미일치 시 {@link ErrorCode#INVALID_VERIFICATION_CODE}, 만료 시 {@link
   * ErrorCode#VERIFICATION_CODE_EXPIRED}.
   */
  @Transactional
  public void verifyAndConsume(Long userId, String code) {
    findValidOrThrow(userId, code).consume();
  }

  /** 미소비 최신 코드를 찾아 만료/일치 검증 후 엔티티 반환. 실패 시 예외. */
  private WithdrawalVerification findValidOrThrow(Long userId, String code) {
    WithdrawalVerification v =
        repository
            .findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE));
    if (v.isExpired()) {
      throw new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED);
    }
    if (code == null || !v.getCode().equals(code)) {
      throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
    }
    return v;
  }

  private String generateCode() {
    SecureRandom random = new SecureRandom();
    int code = 100000 + random.nextInt(900000);
    return String.valueOf(code);
  }
}
