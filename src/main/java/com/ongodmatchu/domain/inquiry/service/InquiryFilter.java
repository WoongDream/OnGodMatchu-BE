package com.ongodmatchu.domain.inquiry.service;

import com.ongodmatchu.domain.inquiry.entity.InquiryStatus;

/** 백오피스 목록 필터 탭 — 전체 / 대기 / 처리중 / 완료. */
public enum InquiryFilter {
  ALL(null),
  PENDING(InquiryStatus.PENDING),
  IN_PROGRESS(InquiryStatus.IN_PROGRESS),
  DONE(InquiryStatus.DONE);

  private final InquiryStatus status;

  InquiryFilter(InquiryStatus status) {
    this.status = status;
  }

  public InquiryStatus status() {
    return status;
  }
}
