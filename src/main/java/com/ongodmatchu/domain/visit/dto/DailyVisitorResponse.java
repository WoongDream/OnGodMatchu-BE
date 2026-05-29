package com.ongodmatchu.domain.visit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record DailyVisitorResponse(
    @Schema(description = "KST 날짜", example = "2026-05-28") LocalDate date,
    @Schema(description = "해당 날짜의 고유 방문자 수") long visitorCount) {}
