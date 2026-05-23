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
