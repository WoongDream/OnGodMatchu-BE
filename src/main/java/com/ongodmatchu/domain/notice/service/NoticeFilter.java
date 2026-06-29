package com.ongodmatchu.domain.notice.service;

import com.ongodmatchu.domain.notice.entity.NoticeStatus;

/** 백오피스 목록 필터 탭 — 고정 = 게시 중 pinned, 게시 = 게시 전체(고정 포함), 임시저장 = DRAFT. */
public enum NoticeFilter {
  ALL(null, null),
  PINNED(NoticeStatus.PUBLISHED, Boolean.TRUE),
  PUBLISHED(NoticeStatus.PUBLISHED, null),
  DRAFT(NoticeStatus.DRAFT, null);

  private final NoticeStatus status;
  private final Boolean pinned;

  NoticeFilter(NoticeStatus status, Boolean pinned) {
    this.status = status;
    this.pinned = pinned;
  }

  public NoticeStatus status() {
    return status;
  }

  public Boolean pinned() {
    return pinned;
  }
}
