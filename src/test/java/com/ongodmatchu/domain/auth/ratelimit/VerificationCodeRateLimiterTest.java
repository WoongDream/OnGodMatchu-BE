package com.ongodmatchu.domain.auth.ratelimit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ongodmatchu.global.exception.RateLimitException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerificationCodeRateLimiterTest {

  private static final String SIGNUP = "signup";
  private static final String WITHDRAWAL = "withdrawal";

  private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
  private final VerificationCodeRateLimiter limiter = new VerificationCodeRateLimiter(clock);

  @Test
  @DisplayName("첫_요청_허용")
  void first_call_allowed() {
    assertThatCode(() -> limiter.check("a@example.com", "1.1.1.1", SIGNUP))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("동일_이메일_60초_내_재요청_차단_retryAfter반환")
  void cooldown_60s_blocks_second() {
    limiter.check("a@example.com", "1.1.1.1", SIGNUP);
    clock.advance(Duration.ofSeconds(30));

    assertThatThrownBy(() -> limiter.check("a@example.com", "2.2.2.2", SIGNUP))
        .isInstanceOf(RateLimitException.class)
        .extracting(e -> ((RateLimitException) e).getRetryAfterSeconds())
        .isEqualTo(30L);
  }

  @Test
  @DisplayName("동일_이메일_60초_경과후_허용")
  void cooldown_passes_after_60s() {
    limiter.check("a@example.com", "1.1.1.1", SIGNUP);
    clock.advance(Duration.ofSeconds(60));
    assertThatCode(() -> limiter.check("a@example.com", "1.1.1.1", SIGNUP))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("동일_이메일_1시간_5회_초과_차단")
  void email_5_per_hour_then_blocked() {
    for (int i = 0; i < 5; i++) {
      limiter.check("e@example.com", "1.1.1.1", SIGNUP);
      clock.advance(Duration.ofMinutes(2));
    }
    assertThatThrownBy(() -> limiter.check("e@example.com", "1.1.1.1", SIGNUP))
        .isInstanceOf(RateLimitException.class);
  }

  @Test
  @DisplayName("동일_IP_1시간_10회_초과_차단_(이메일은_매번_다름)")
  void ip_10_per_hour_then_blocked() {
    for (int i = 0; i < 10; i++) {
      limiter.check("user" + i + "@example.com", "9.9.9.9", SIGNUP);
      clock.advance(Duration.ofMinutes(1));
    }
    assertThatThrownBy(() -> limiter.check("user99@example.com", "9.9.9.9", SIGNUP))
        .isInstanceOf(RateLimitException.class);
  }

  @Test
  @DisplayName("1시간_경과시_카운트_리셋")
  void window_resets_after_one_hour() {
    for (int i = 0; i < 5; i++) {
      limiter.check("e@example.com", "1.1.1.1", SIGNUP);
      clock.advance(Duration.ofMinutes(2));
    }
    clock.advance(Duration.ofHours(1));
    assertThatCode(() -> limiter.check("e@example.com", "1.1.1.1", SIGNUP))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("IP_null_허용_(IP_제한_미적용)")
  void null_ip_skips_ip_check() {
    for (int i = 0; i < 4; i++) {
      limiter.check("u" + i + "@example.com", null, SIGNUP);
      clock.advance(Duration.ofMinutes(1));
    }
    assertThatCode(() -> limiter.check("u99@example.com", null, SIGNUP)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("purpose_분리_signup_쿨다운이_withdrawal_에는_영향없음")
  void purpose_isolation_cooldown() {
    limiter.check("a@example.com", "1.1.1.1", SIGNUP);
    // 같은 이메일이라도 purpose 가 다르면 별도 카운팅 — 쿨다운 무관하게 즉시 발송 가능
    assertThatCode(() -> limiter.check("a@example.com", "1.1.1.1", WITHDRAWAL))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("purpose_분리_signup_5회_한도가_withdrawal_에는_영향없음")
  void purpose_isolation_email_window() {
    for (int i = 0; i < 5; i++) {
      limiter.check("e@example.com", "1.1.1.1", SIGNUP);
      clock.advance(Duration.ofMinutes(2));
    }
    // signup 한도 초과 상태에서도 withdrawal 카운터는 깨끗함
    assertThatCode(() -> limiter.check("e@example.com", "1.1.1.1", WITHDRAWAL))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("검증_쿨다운_없음_연속_호출_한도직전까지_통과")
  void verify_no_cooldown_consecutive_calls_until_limit() {
    // 발송 check 와 달리 시간이 거의 안 흘러도 한도 직전(9회)까지 예외 없이 통과
    assertThatCode(
            () -> {
              for (int i = 0; i < 9; i++) {
                limiter.checkVerifyAttempt("a@example.com", "1.1.1.1", SIGNUP);
              }
            })
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("검증_동일_이메일_1시간_10회_초과_차단")
  void verify_email_10_per_hour_then_blocked() {
    for (int i = 0; i < 10; i++) {
      limiter.checkVerifyAttempt("e@example.com", "1.1.1.1", SIGNUP);
    }
    assertThatThrownBy(() -> limiter.checkVerifyAttempt("e@example.com", "1.1.1.1", SIGNUP))
        .isInstanceOf(RateLimitException.class);
  }

  @Test
  @DisplayName("검증_동일_IP_1시간_20회_초과_차단_(이메일은_매번_다름)")
  void verify_ip_20_per_hour_then_blocked() {
    for (int i = 0; i < 20; i++) {
      limiter.checkVerifyAttempt("user" + i + "@example.com", "9.9.9.9", SIGNUP);
    }
    assertThatThrownBy(() -> limiter.checkVerifyAttempt("user99@example.com", "9.9.9.9", SIGNUP))
        .isInstanceOf(RateLimitException.class);
  }

  @Test
  @DisplayName("검증_1시간_경과시_카운트_리셋")
  void verify_window_resets_after_one_hour() {
    for (int i = 0; i < 10; i++) {
      limiter.checkVerifyAttempt("e@example.com", "1.1.1.1", SIGNUP);
    }
    // prune 은 윈도우보다 '이전' 기록만 제거하므로 정확히 1시간이 아니라 살짝 더 진행시켜야 리셋된다
    clock.advance(Duration.ofHours(1).plusSeconds(1));
    assertThatCode(() -> limiter.checkVerifyAttempt("e@example.com", "1.1.1.1", SIGNUP))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("발송_카운터와_분리_검증을_한도까지_채워도_발송_카운트_무관")
  void verify_separate_from_send_counter() {
    // 검증(checkVerifyAttempt) 을 이메일 한도(10회) 까지 가득 채운다
    for (int i = 0; i < 10; i++) {
      limiter.checkVerifyAttempt("e@example.com", "1.1.1.1", SIGNUP);
    }
    // 11회째 검증은 막히지만,
    assertThatThrownBy(() -> limiter.checkVerifyAttempt("e@example.com", "1.1.1.1", SIGNUP))
        .isInstanceOf(RateLimitException.class);
    // 같은 email/purpose 의 발송 카운터는 별도 맵이라 영향 없음 — 발송은 통과
    assertThatCode(() -> limiter.check("e@example.com", "1.1.1.1", SIGNUP))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("검증_IP_null_허용_(IP_제한_미적용_이메일만_집계)")
  void verify_null_ip_skips_ip_check() {
    // IP 집계를 건너뛰므로 서로 다른 이메일이면 IP 한도(20) 와 무관하게 통과
    assertThatCode(
            () -> {
              for (int i = 0; i < 25; i++) {
                limiter.checkVerifyAttempt("u" + i + "@example.com", null, SIGNUP);
              }
            })
        .doesNotThrowAnyException();
  }

  // 테스트용 mutable clock — Clock.fixed 는 advance 안 되니 직접 구현
  private static class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant initial) {
      this.now = initial;
    }

    void advance(Duration d) {
      this.now = this.now.plus(d);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
