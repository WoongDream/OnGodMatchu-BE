package com.ongodmatchu.domain.inquiry.dto;

import com.ongodmatchu.domain.inquiry.entity.Inquiry;
import com.ongodmatchu.domain.notification.dto.NotificationResponse;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/** 본인 문의 카드 — 제목/내용/상태 + 받은 답변(연결 알림). 답변 받기 전이면 answers 빈 배열. */
public record InquiryResponse(
    Long id,
    String title,
    String content,
    @Schema(description = "PENDING/IN_PROGRESS/DONE") String status,
    @Schema(description = "대기/처리중/완료") String statusLabel,
    OffsetDateTime createdAt,
    @Schema(description = "받은 답변(연결 알림). 받기 전이면 빈 배열") List<NotificationResponse> answers) {

  public static InquiryResponse from(Inquiry inquiry, List<NotificationResponse> answers) {
    return new InquiryResponse(
        inquiry.getId(),
        inquiry.getTitle(),
        inquiry.getContent(),
        inquiry.getStatus().name(),
        inquiry.getStatus().getLabel(),
        TimeFormat.toResponse(inquiry.getCreatedAt()),
        answers);
  }
}
