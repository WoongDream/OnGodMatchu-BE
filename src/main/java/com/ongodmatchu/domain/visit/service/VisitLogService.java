package com.ongodmatchu.domain.visit.service;

import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.domain.visit.dto.DailyVisitorResponse;
import com.ongodmatchu.domain.visit.dto.DauResponse;
import com.ongodmatchu.domain.visit.dto.VisitorSummaryResponse;
import com.ongodmatchu.domain.visit.entity.VisitLog;
import com.ongodmatchu.domain.visit.repository.VisitLogRepository;
import com.ongodmatchu.domain.visit.repository.VisitLogRepository.DailyVisitorRow;
import com.ongodmatchu.global.config.AsyncConfig;
import com.ongodmatchu.global.util.TimeFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisitLogService {

  private static final int PATH_MAX_LENGTH = 255;

  private final VisitLogRepository visitLogRepository;
  private final UserRepository userRepository;

  /** 페이지 방문 1건 적재. 비동기 전용 — 호출자는 응답 지연 없이 즉시 리턴. 실패는 swallow + log. */
  @Async(AsyncConfig.VISIT_LOG_EXECUTOR)
  @Transactional
  public void recordVisit(String anonId, Long userId, String path) {
    if (anonId == null || anonId.isBlank()) {
      return;
    }
    if (path == null || path.isBlank()) {
      return;
    }
    String safePath = path.length() > PATH_MAX_LENGTH ? path.substring(0, PATH_MAX_LENGTH) : path;
    try {
      User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
      visitLogRepository.save(VisitLog.builder().anonId(anonId).user(user).path(safePath).build());
    } catch (RuntimeException ex) {
      log.warn("visit_log insert failed (anonId={}, userId={}): {}", anonId, userId, ex.toString());
    }
  }

  /** KST 기준 특정 날짜의 DAU. */
  @Transactional(readOnly = true)
  public DauResponse getDau(LocalDate date) {
    LocalDate target = date != null ? date : LocalDate.now(TimeFormat.SERVER_OFFSET);
    LocalDateTime start = target.atStartOfDay();
    LocalDateTime end = target.plusDays(1).atStartOfDay();
    long count = visitLogRepository.countDistinctVisitors(start, end);
    return new DauResponse(target, count);
  }

  private static final int DAILY_WINDOW_DAYS = 7;

  /** 헤더 방문자 블록용 요약 — 오늘(KST) + 전체 누적 + 최근 7일(오늘 포함) 일자별 카운트. 적재 없는 날은 0 으로 채워 항상 7개 반환. */
  @Transactional(readOnly = true)
  public VisitorSummaryResponse getVisitorSummary() {
    LocalDate today = LocalDate.now(TimeFormat.SERVER_OFFSET);
    LocalDate windowStart = today.minusDays(DAILY_WINDOW_DAYS - 1L);
    LocalDateTime start = windowStart.atStartOfDay();
    LocalDateTime end = today.plusDays(1).atStartOfDay();

    Map<LocalDate, Long> counts = new HashMap<>();
    for (DailyVisitorRow row : visitLogRepository.countDistinctVisitorsDaily(start, end)) {
      counts.put(row.getD().toLocalDate(), row.getC());
    }

    List<DailyVisitorResponse> daily = new ArrayList<>(DAILY_WINDOW_DAYS);
    for (int i = 0; i < DAILY_WINDOW_DAYS; i++) {
      LocalDate d = windowStart.plusDays(i);
      daily.add(new DailyVisitorResponse(d, counts.getOrDefault(d, 0L)));
    }

    long todayCount = counts.getOrDefault(today, 0L);
    long total = visitLogRepository.countTotalVisitors();
    return new VisitorSummaryResponse(todayCount, total, daily);
  }
}
