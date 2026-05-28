package com.ongodmatchu.domain.visit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record DauResponse(
    @Schema(description = "KST 기준 집계 날짜", example = "2026-05-28") LocalDate date,
    @Schema(description = "고유 방문자 수 (로그인 user_id + 비로그인 anon_id DISTINCT)") long visitorCount) {}
