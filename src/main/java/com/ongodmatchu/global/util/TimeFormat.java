package com.ongodmatchu.global.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 응답 DTO 시간 직렬화 헬퍼.
 *
 * <p>Entity / DB 는 {@link LocalDateTime} (서버 KST 가정), 응답은 {@link OffsetDateTime} 으로 ISO 8601 +
 * offset (예: {@code 2026-05-07T10:00:00+09:00}) 형태로 내보낸다. 클라이언트 타임존 해석 모호성 (Safari iOS UTC 해석 등)
 * 회피.
 */
public final class TimeFormat {

  /** 서버 운영 타임존. DB 시간이 이 타임존으로 저장되어 있다고 가정. */
  public static final ZoneOffset SERVER_OFFSET = ZoneOffset.of("+09:00");

  private TimeFormat() {}

  public static OffsetDateTime toResponse(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atOffset(SERVER_OFFSET);
  }
}
