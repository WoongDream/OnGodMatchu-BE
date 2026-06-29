package com.ongodmatchu.domain.inquiry.dto;

import com.ongodmatchu.domain.inquiry.entity.Inquiry;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** BO 목록 아이템 — 제목 + 문의 유저(프로필이미지/닉네임) + 접수일 + 상태. */
public record AdminInquiryListItemResponse(
    Long id,
    String title,
    InquiryAuthorResponse author,
    @Schema(description = "PENDING/IN_PROGRESS/DONE") String status,
    @Schema(description = "대기/처리중/완료") String statusLabel,
    @Schema(description = "접수일") OffsetDateTime createdAt) {

  public static AdminInquiryListItemResponse from(Inquiry inquiry, String authorImageUrl) {
    return new AdminInquiryListItemResponse(
        inquiry.getId(),
        inquiry.getTitle(),
        InquiryAuthorResponse.of(inquiry.getUser(), authorImageUrl),
        inquiry.getStatus().name(),
        inquiry.getStatus().getLabel(),
        TimeFormat.toResponse(inquiry.getCreatedAt()));
  }
}
