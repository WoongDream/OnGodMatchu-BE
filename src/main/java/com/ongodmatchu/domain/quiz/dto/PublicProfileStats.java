package com.ongodmatchu.domain.quiz.dto;

/**
 * 타인 시점 프로필 요약 통계 — 전부 PUBLIC 퀴즈 기준 집계 (비공개 퀴즈는 타인 노출/통계 제외). UserService 가 식별 정보와 합쳐 {@code
 * PublicProfileSummaryResponse} 로 조립한다.
 */
public record PublicProfileStats(
    long quizCount,
    long totalPlayCount,
    long totalStarCount,
    long solvedCount,
    Double avgSolveRate) {}
