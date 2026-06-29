package com.ongodmatchu.domain.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 정지 일수(N일). suspendedUntil = now + days. 영구정지는 미지원(일수 기반만). */
public record SuspendRequest(
    @Min(value = 1, message = "정지 일수는 1일 이상이어야 합니다.") @Max(value = 3650, message = "정지 일수가 너무 큽니다.")
        int days) {}
