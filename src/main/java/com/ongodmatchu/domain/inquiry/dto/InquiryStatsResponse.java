package com.ongodmatchu.domain.inquiry.dto;

/** 백오피스 상단 stats 카드 — 전체 / 대기 / 처리중 / 완료. */
public record InquiryStatsResponse(long total, long pending, long inProgress, long done) {}
