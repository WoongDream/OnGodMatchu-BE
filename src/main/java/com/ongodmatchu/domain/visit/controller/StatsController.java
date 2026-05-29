package com.ongodmatchu.domain.visit.controller;

import com.ongodmatchu.domain.visit.dto.DauResponse;
import com.ongodmatchu.domain.visit.dto.VisitorSummaryResponse;
import com.ongodmatchu.domain.visit.service.VisitLogService;
import com.ongodmatchu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Tag(name = "Stats", description = "서비스 통계 (DAU 등). 비로그인 허용 — 공개 지표")
public class StatsController {

  private final VisitLogService visitLogService;

  @Operation(
      summary = "오늘 방문자 수 (DAU)",
      description =
          "KST 기준 지정 일자의 고유 방문자 수. date 미지정 시 오늘. "
              + "고유 방문자는 로그인 user_id 또는 비로그인 anon_id 로 DISTINCT 집계 (둘이 동시에 카운트되지 않음).")
  @GetMapping("/dau")
  public ResponseEntity<ApiResponse<DauResponse>> getDau(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return ResponseEntity.ok(ApiResponse.ok(visitLogService.getDau(date)));
  }

  @Operation(
      summary = "헤더 방문자 블록용 요약 (오늘 + 누적 + 최근 7일)",
      description =
          "KST 오늘 고유 방문자 수, 서비스 전체 누적 고유 방문자 수, 최근 7일(오늘 포함, 오름차순) 일자별 카운트를 한 번에 반환. "
              + "적재 없는 날은 0 으로 채워 daily 는 항상 7개. 비로그인 허용.")
  @GetMapping("/visitors")
  public ResponseEntity<ApiResponse<VisitorSummaryResponse>> getVisitorSummary() {
    return ResponseEntity.ok(ApiResponse.ok(visitLogService.getVisitorSummary()));
  }
}
