package com.ongodmatchu.domain.visit.repository;

import com.ongodmatchu.domain.visit.entity.VisitLog;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitLogRepository extends JpaRepository<VisitLog, Long> {

  /**
   * 지정 구간 [start, end) 의 고유 방문자 수. 로그인 사용자는 user_id 로, 비로그인은 anon_id 로 식별 (둘이 동시에 카운트되지 않도록
   * COALESCE).
   */
  @Query(
      value =
          "SELECT COUNT(DISTINCT COALESCE(user_id::text, anon_id)) "
              + "FROM visit_log "
              + "WHERE visited_at >= :start AND visited_at < :end",
      nativeQuery = true)
  long countDistinctVisitors(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  /**
   * 전 기간 누적 방문자 수 — 일자별 고유 방문자 수(서버 KST)를 모든 날에 대해 합산. 같은 사람이 다른 날 방문하면 날마다 1씩 잡혀, 헤더 TOTAL = 일자별
   * 방문자수의 합 ("어제 4 + 오늘 3 = 7"). 데이터셋이 커지면 daily snapshot 으로 전환 필요.
   */
  @Query(
      value =
          "SELECT COALESCE(SUM(c), 0)::bigint FROM ("
              + "  SELECT COUNT(DISTINCT COALESCE(user_id::text, anon_id)) AS c "
              + "  FROM visit_log "
              + "  GROUP BY date_trunc('day', visited_at)"
              + ") daily",
      nativeQuery = true)
  long countTotalVisitors();

  /**
   * [start, end) 구간 일자별 고유 방문자 수 (서버 KST). visited_at 이 LocalDateTime (서버 timezone) 으로 저장되므로
   * date_trunc 결과도 KST 일자. 결과는 [date, count] 튜플 — 적재 없는 날은 행이 빠진다 (호출자가 7일 채워야 함).
   */
  @Query(
      value =
          "SELECT date_trunc('day', visited_at)::date AS d, "
              + "       COUNT(DISTINCT COALESCE(user_id::text, anon_id)) AS c "
              + "FROM visit_log "
              + "WHERE visited_at >= :start AND visited_at < :end "
              + "GROUP BY d "
              + "ORDER BY d",
      nativeQuery = true)
  List<DailyVisitorRow> countDistinctVisitorsDaily(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  /** native query projection — 컬럼명 그대로 alias 매핑. */
  interface DailyVisitorRow {
    Date getD();

    long getC();
  }
}
