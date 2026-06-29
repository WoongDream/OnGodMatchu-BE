package com.ongodmatchu.domain.inquiry.dto;

import com.ongodmatchu.domain.inquiry.entity.InquiryStatus;
import jakarta.validation.constraints.NotNull;

/** BO 처리 상태 변경 — PENDING/IN_PROGRESS/DONE. 수동 전환(답변 발송과 독립). */
public record InquiryStatusUpdateRequest(@NotNull InquiryStatus status) {}
