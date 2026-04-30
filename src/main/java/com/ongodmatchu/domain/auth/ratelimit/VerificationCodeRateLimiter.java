package com.ongodmatchu.domain.auth.ratelimit;

import com.ongodmatchu.global.exception.RateLimitException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * 이메일 인증 코드 발송용 in-memory rate limiter. 정책: 동일 이메일 60초 쿨다운 / 동일 이메일 1시간 5회 / 동일 IP 1시간 10회. 단일 인스턴스
 * 가정 — 다중 인스턴스로 가면 Redis/Bucket4j 로 교체.
 */
@Component
public class VerificationCodeRateLimiter {

  static final Duration EMAIL_COOLDOWN = Duration.ofSeconds(60);
  static final Duration WINDOW = Duration.ofHours(1);
  static final int EMAIL_LIMIT_PER_WINDOW = 5;
  static final int IP_LIMIT_PER_WINDOW = 10;

  private final Clock clock;
  private final ConcurrentMap<String, Deque<Instant>> emailHits = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Deque<Instant>> ipHits = new ConcurrentHashMap<>();

  public VerificationCodeRateLimiter() {
    this(Clock.systemUTC());
  }

  VerificationCodeRateLimiter(Clock clock) {
    this.clock = clock;
  }

  public synchronized void check(String email, String ipAddress) {
    Instant now = clock.instant();
    Deque<Instant> emailQueue = emailHits.computeIfAbsent(email, k -> new ArrayDeque<>());
    Deque<Instant> ipQueue =
        ipAddress == null ? null : ipHits.computeIfAbsent(ipAddress, k -> new ArrayDeque<>());

    pruneOlderThan(emailQueue, now.minus(WINDOW));
    if (ipQueue != null) {
      pruneOlderThan(ipQueue, now.minus(WINDOW));
    }

    Instant lastEmail = emailQueue.peekLast();
    if (lastEmail != null) {
      long sinceLast = Duration.between(lastEmail, now).getSeconds();
      if (sinceLast < EMAIL_COOLDOWN.getSeconds()) {
        throw new RateLimitException(EMAIL_COOLDOWN.getSeconds() - sinceLast);
      }
    }

    if (emailQueue.size() >= EMAIL_LIMIT_PER_WINDOW) {
      Instant oldest = emailQueue.peekFirst();
      long retry = Duration.between(now, oldest.plus(WINDOW)).getSeconds();
      throw new RateLimitException(Math.max(retry, 1));
    }

    if (ipQueue != null && ipQueue.size() >= IP_LIMIT_PER_WINDOW) {
      Instant oldest = ipQueue.peekFirst();
      long retry = Duration.between(now, oldest.plus(WINDOW)).getSeconds();
      throw new RateLimitException(Math.max(retry, 1));
    }

    emailQueue.addLast(now);
    if (ipQueue != null) {
      ipQueue.addLast(now);
    }
  }

  private void pruneOlderThan(Deque<Instant> queue, Instant threshold) {
    Iterator<Instant> it = queue.iterator();
    while (it.hasNext()) {
      Instant t = it.next();
      if (t.isBefore(threshold)) {
        it.remove();
      } else {
        return;
      }
    }
  }
}
