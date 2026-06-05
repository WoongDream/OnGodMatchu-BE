package com.ongodmatchu.domain.inquiry.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 문의 처리 상태. 대기 → 처리중 → 완료. 전환은 BO 수동(저장)으로만, 답변 발송과 독립. */
@Getter
@RequiredArgsConstructor
public enum InquiryStatus {
  PENDING("대기"),
  IN_PROGRESS("처리중"),
  DONE("완료");

  private final String label;
}
