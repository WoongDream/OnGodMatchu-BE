package com.ongodmatchu.domain.inquiry.dto;

import com.ongodmatchu.domain.inquiry.entity.Inquiry;
import com.ongodmatchu.domain.notification.dto.NotificationResponse;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/** BO 상세 — 제목/내용 + 문의 유저(프로필 모달용) + 처리 상태 + 보낸 답변 목록. */
public record AdminInquiryDetailResponse(
    Long id,
    String title,
    String content,
    InquiryAuthorResponse author,
    @Schema(description = "PENDING/IN_PROGRESS/DONE") String status,
    @Schema(description = "대기/처리중/완료") String statusLabel,
    @Schema(description = "접수일") OffsetDateTime createdAt,
    @Schema(description = "보낸 답변(연결 알림), 오래된 순") List<NotificationResponse> answers) {

  public static AdminInquiryDetailResponse of(
      Inquiry inquiry, String authorImageUrl, List<NotificationResponse> answers) {
    return new AdminInquiryDetailResponse(
        inquiry.getId(),
        inquiry.getTitle(),
        inquiry.getContent(),
        InquiryAuthorResponse.of(inquiry.getUser(), authorImageUrl),
        inquiry.getStatus().name(),
        inquiry.getStatus().getLabel(),
        TimeFormat.toResponse(inquiry.getCreatedAt()),
        answers);
  }
}
