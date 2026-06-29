package com.ongodmatchu.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 유저 관리 조회 화면 상단 통계 카드. 시스템 계정 제외, 활성(비탈퇴) 기준. */
public record AdminUserSummaryResponse(
    @Schema(description = "전체 활성 유저 수") long totalUsers,
    long ownerCount,
    long adminCount,
    long userCount,
    @Schema(description = "현재 정지 중인 유저 수") long suspendedCount,
    @Schema(description = "이번 달(KST) 신규 가입 수") long newThisMonth) {}
