package com.ongodmatchu.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 사용자 알림 유형 — 안내(INFO) / 경고(WARNING). */
@Getter
@RequiredArgsConstructor
public enum NotificationType {
  INFO("안내"),
  WARNING("경고");

  private final String label;
}
