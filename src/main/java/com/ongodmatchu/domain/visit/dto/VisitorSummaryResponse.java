package com.ongodmatchu.domain.visit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record VisitorSummaryResponse(
    @Schema(description = "오늘(KST) 고유 방문자 수") long today,
    @Schema(description = "전체 누적 고유 방문자 수 (서비스 시작 이후 전체)") long total,
    @Schema(description = "최근 7일(오늘 포함) 일자별 고유 방문자 수, 오름차순. 데이터 없는 날은 0 으로 채움")
        List<DailyVisitorResponse> daily) {}
