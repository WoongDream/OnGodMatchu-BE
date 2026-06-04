package com.ongodmatchu.domain.notice.dto;

/** 백오피스 상단 stats 카드 — 전체 / 게시(미고정) / 고정 / 임시저장. */
public record NoticeStatsResponse(long total, long published, long pinned, long draft) {}
