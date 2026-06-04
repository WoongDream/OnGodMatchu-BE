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
 * 이메일 인증 코드 in-memory rate limiter. 단일 인스턴스 가정 — 다중 인스턴스로 가면 Redis/Bucket4j 로 교체.
 *
 * <ul>
 *   <li>발송({@link #check}) — 동일 이메일 60초 쿨다운 / 동일 이메일 1시간 5회 / 동일 IP 1시간 10회
 *   <li>검증 시도({@link #checkVerifyAttempt}) — 쿨다운 없음(정상 사용자의 연속 입력 허용) / 동일 이메일 1시간 10회 / 동일 IP 1시간
 *       20회. 6자리 코드 brute-force 방어용. 발송 카운터와 별도 맵으로 분리.
 * </ul>
 */
@Component
public class VerificationCodeRateLimiter {

  static final Duration EMAIL_COOLDOWN = Duration.ofSeconds(60);
  static final Duration WINDOW = Duration.ofHours(1);
  static final int EMAIL_LIMIT_PER_WINDOW = 5;
  static final int IP_LIMIT_PER_WINDOW = 10;
  static final int VERIFY_EMAIL_LIMIT_PER_WINDOW = 10;
  static final int VERIFY_IP_LIMIT_PER_WINDOW = 20;

  private final Clock clock;
  private final ConcurrentMap<String, Deque<Instant>> emailHits = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Deque<Instant>> ipHits = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Deque<Instant>> verifyEmailHits = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Deque<Instant>> verifyIpHits = new ConcurrentHashMap<>();

  public VerificationCodeRateLimiter() {
    this(Clock.systemUTC());
  }

  VerificationCodeRateLimiter(Clock clock) {
    this.clock = clock;
  }

  /**
   * 이메일/IP 별 발송 한도 체크. {@code purpose} 로 가입(signup)·회원탈퇴(withdrawal) 등 컨텍스트를 분리해 한도가 합산되지 않도록 한다.
   */
  public synchronized void check(String email, String ipAddress, String purpose) {
    Instant now = clock.instant();
    String emailKey = purpose + ":" + email;
    String ipKey = ipAddress == null ? null : purpose + ":" + ipAddress;
    Deque<Instant> emailQueue = emailHits.computeIfAbsent(emailKey, k -> new ArrayDeque<>());
    Deque<Instant> ipQueue =
        ipKey == null ? null : ipHits.computeIfAbsent(ipKey, k -> new ArrayDeque<>());

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

    enforceWindowLimit(emailQueue, now, EMAIL_LIMIT_PER_WINDOW);
    if (ipQueue != null) {
      enforceWindowLimit(ipQueue, now, IP_LIMIT_PER_WINDOW);
    }

    emailQueue.addLast(now);
    if (ipQueue != null) {
      ipQueue.addLast(now);
    }
  }

  /**
   * 코드 검증 시도 한도 체크. 발송과 달리 쿨다운이 없어 정상 사용자의 연속 입력을 막지 않으면서, 동일 이메일/IP 의 시간당 시도 횟수만 제한해 brute-force 를
   * 차단한다. 성공/실패 무관하게 호출당 1회 집계 (검증 직전에 호출).
   */
  public synchronized void checkVerifyAttempt(String email, String ipAddress, String purpose) {
    Instant now = clock.instant();
    String emailKey = purpose + ":" + email;
    String ipKey = ipAddress == null ? null : purpose + ":" + ipAddress;
    Deque<Instant> emailQueue = verifyEmailHits.computeIfAbsent(emailKey, k -> new ArrayDeque<>());
    Deque<Instant> ipQueue =
        ipKey == null ? null : verifyIpHits.computeIfAbsent(ipKey, k -> new ArrayDeque<>());

    pruneOlderThan(emailQueue, now.minus(WINDOW));
    if (ipQueue != null) {
      pruneOlderThan(ipQueue, now.minus(WINDOW));
    }

    enforceWindowLimit(emailQueue, now, VERIFY_EMAIL_LIMIT_PER_WINDOW);
    if (ipQueue != null) {
      enforceWindowLimit(ipQueue, now, VERIFY_IP_LIMIT_PER_WINDOW);
    }

    emailQueue.addLast(now);
    if (ipQueue != null) {
      ipQueue.addLast(now);
    }
  }

  /** 윈도우 내 누적 횟수가 한도 이상이면 가장 오래된 기록 만료 시점까지의 Retry-After 로 예외. 큐는 호출 전 prune 됐다고 가정. */
  private void enforceWindowLimit(Deque<Instant> queue, Instant now, int limit) {
    if (queue.size() >= limit) {
      Instant oldest = queue.peekFirst();
      long retry = Duration.between(now, oldest.plus(WINDOW)).getSeconds();
      throw new RateLimitException(Math.max(retry, 1));
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
