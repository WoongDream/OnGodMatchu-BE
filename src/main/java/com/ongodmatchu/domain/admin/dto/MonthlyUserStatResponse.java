package com.ongodmatchu.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 월별 유저 추이 (오래된 달 → 최신 달 순으로 반환). */
public record MonthlyUserStatResponse(
    @Schema(description = "yyyy-MM") String yearMonth,
    @Schema(description = "월말 시점 누적 활성 유저 수 (이번 달은 현재 시점)") long cumulative,
    @Schema(description = "그 달 신규 가입 수") long newCount,
    @Schema(description = "그 달 이탈(탈퇴) 수") long churnCount) {}
