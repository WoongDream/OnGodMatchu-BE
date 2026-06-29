package com.ongodmatchu.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.ongodmatchu.domain.auth.ratelimit.VerificationCodeRateLimiter;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.entity.WithdrawalVerification;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.domain.user.repository.WithdrawalVerificationRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.global.exception.RateLimitException;
import com.ongodmatchu.infra.mail.MailService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WithdrawalCodeServiceTest {

  @InjectMocks private WithdrawalCodeService service;
  @Mock private UserRepository userRepository;
  @Mock private MailService mailService;
  @Mock private VerificationCodeRateLimiter rateLimiter;
  @Mock private WithdrawalVerificationRepository repository;

  private User buildUser() {
    User user =
        User.builder()
            .email("user@example.com")
            .nickname("유저")
            .password("hashed")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    ReflectionTestUtils.setField(user, "publicId", UUID.randomUUID());
    return user;
  }

  // ============ sendCode ============

  @Test
  @DisplayName("sendCode_정상_이전코드삭제_새코드저장_메일발송_RateLimit체크")
  void sendCode_success() {
    User user = buildUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    service.sendCode(1L, "1.2.3.4");

    then(rateLimiter).should().check("user@example.com", "1.2.3.4", "withdrawal");
    then(repository).should().deleteByUserId(1L);

    ArgumentCaptor<WithdrawalVerification> captor =
        ArgumentCaptor.forClass(WithdrawalVerification.class);
    then(repository).should().save(captor.capture());
    WithdrawalVerification saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(1L);
    assertThat(saved.getCode()).hasSize(6).matches("\\d{6}");
    assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(4));

    then(mailService).should().sendWithdrawalCode("user@example.com", saved.getCode());
  }

  @Test
  @DisplayName("sendCode_사용자미존재_USER_NOT_FOUND")
  void sendCode_userNotFound() {
    given(userRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.sendCode(99L, "1.2.3.4"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);

    then(rateLimiter).shouldHaveNoInteractions();
    then(mailService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("sendCode_RateLimit초과_RateLimitException_저장및메일미발생")
  void sendCode_rateLimited() {
    User user = buildUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    willThrow(new RateLimitException(42))
        .given(rateLimiter)
        .check("user@example.com", "1.2.3.4", "withdrawal");

    assertThatThrownBy(() -> service.sendCode(1L, "1.2.3.4"))
        .isInstanceOf(RateLimitException.class);

    then(repository).should(never()).save(any());
    then(mailService).shouldHaveNoInteractions();
  }

  // ============ verifyAndConsume ============

  @Test
  @DisplayName("verifyAndConsume_정상_consume호출")
  void verifyAndConsume_success() {
    WithdrawalVerification v =
        WithdrawalVerification.builder()
            .userId(1L)
            .code("123456")
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
    given(repository.findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(1L))
        .willReturn(Optional.of(v));

    service.verifyAndConsume(1L, "123456");

    assertThat(v.isConsumed()).isTrue();
  }

  @Test
  @DisplayName("verifyAndConsume_미존재_INVALID_VERIFICATION_CODE")
  void verifyAndConsume_notFound() {
    given(repository.findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(1L))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.verifyAndConsume(1L, "123456"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);
  }

  @Test
  @DisplayName("verifyAndConsume_만료_VERIFICATION_CODE_EXPIRED")
  void verifyAndConsume_expired() {
    WithdrawalVerification v =
        WithdrawalVerification.builder()
            .userId(1L)
            .code("123456")
            .expiresAt(LocalDateTime.now().minusMinutes(1))
            .build();
    given(repository.findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(1L))
        .willReturn(Optional.of(v));

    assertThatThrownBy(() -> service.verifyAndConsume(1L, "123456"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED);
    assertThat(v.isConsumed()).isFalse();
  }

  @Test
  @DisplayName("verifyAndConsume_미일치_INVALID_VERIFICATION_CODE_consume안됨")
  void verifyAndConsume_mismatch() {
    WithdrawalVerification v =
        WithdrawalVerification.builder()
            .userId(1L)
            .code("123456")
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
    given(repository.findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(1L))
        .willReturn(Optional.of(v));

    assertThatThrownBy(() -> service.verifyAndConsume(1L, "999999"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);
    assertThat(v.isConsumed()).isFalse();
  }

  @Test
  @DisplayName("verifyAndConsume_null코드_INVALID_VERIFICATION_CODE")
  void verifyAndConsume_nullCode() {
    WithdrawalVerification v =
        WithdrawalVerification.builder()
            .userId(1L)
            .code("123456")
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
    given(repository.findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(1L))
        .willReturn(Optional.of(v));

    assertThatThrownBy(() -> service.verifyAndConsume(1L, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);
  }

  // ============ verify (dry-run) ============

  @Test
  @DisplayName("verify_정상_예외없이통과_코드미소비_RateLimit체크")
  void verify_success_notConsumed() {
    User user = buildUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    WithdrawalVerification v =
        WithdrawalVerification.builder()
            .userId(1L)
            .code("123456")
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
    given(repository.findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(1L))
        .willReturn(Optional.of(v));

    service.verify(1L, "123456", "1.2.3.4");

    then(rateLimiter).should().checkVerifyAttempt("user@example.com", "1.2.3.4", "withdrawal");
    assertThat(v.isConsumed()).isFalse();
    assertThat(v.getConsumedAt()).isNull();
  }

  @Test
  @DisplayName("verify_RateLimit초과_RateLimitException_코드검증미수행")
  void verify_rateLimited() {
    User user = buildUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    willThrow(new RateLimitException(42))
        .given(rateLimiter)
        .checkVerifyAttempt("user@example.com", "1.2.3.4", "withdrawal");

    assertThatThrownBy(() -> service.verify(1L, "123456", "1.2.3.4"))
        .isInstanceOf(RateLimitException.class);

    then(repository).should(never()).findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(any());
  }

  @Test
  @DisplayName("verify_사용자미존재_USER_NOT_FOUND_RateLimit및코드검증미수행")
  void verify_userNotFound() {
    given(userRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.verify(99L, "123456", "1.2.3.4"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);

    then(rateLimiter).shouldHaveNoInteractions();
    then(repository).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("verify_미존재_INVALID_VERIFICATION_CODE")
  void verify_notFound() {
    User user = buildUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(repository.findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(1L))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.verify(1L, "123456", "1.2.3.4"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);
  }

  @Test
  @DisplayName("verify_만료_VERIFICATION_CODE_EXPIRED")
  void verify_expired() {
    User user = buildUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    WithdrawalVerification v =
        WithdrawalVerification.builder()
            .userId(1L)
            .code("123456")
            .expiresAt(LocalDateTime.now().minusMinutes(1))
            .build();
    given(repository.findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(1L))
        .willReturn(Optional.of(v));

    assertThatThrownBy(() -> service.verify(1L, "123456", "1.2.3.4"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED);
    assertThat(v.isConsumed()).isFalse();
  }

  @Test
  @DisplayName("verify_미일치_INVALID_VERIFICATION_CODE_consume안됨")
  void verify_mismatch() {
    User user = buildUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    WithdrawalVerification v =
        WithdrawalVerification.builder()
            .userId(1L)
            .code("123456")
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
    given(repository.findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(1L))
        .willReturn(Optional.of(v));

    assertThatThrownBy(() -> service.verify(1L, "999999", "1.2.3.4"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);
    assertThat(v.isConsumed()).isFalse();
  }

  @Test
  @DisplayName("verify_null코드_INVALID_VERIFICATION_CODE")
  void verify_nullCode() {
    User user = buildUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    WithdrawalVerification v =
        WithdrawalVerification.builder()
            .userId(1L)
            .code("123456")
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
    given(repository.findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(1L))
        .willReturn(Optional.of(v));

    assertThatThrownBy(() -> service.verify(1L, null, "1.2.3.4"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);
    assertThat(v.isConsumed()).isFalse();
  }

  private static org.mockito.verification.VerificationMode never() {
    return org.mockito.Mockito.never();
  }
}
