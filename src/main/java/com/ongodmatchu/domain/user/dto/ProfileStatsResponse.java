package com.ongodmatchu.domain.user.dto;

/** 내 프로필 (내가 만든 퀴즈) 페이지 상단 통계. {@code weeklyPlayCount} 는 풀이 기록 묶음 머지 전까지 0 반환. */
public record ProfileStatsResponse(
    long totalQuizCount,
    long totalPlayCount,
    long totalStarCount,
    long totalCommentCount,
    long totalShareCount,
    long weeklyPlayCount) {}
