package com.ongodmatchu.domain.notice.dto;

import com.ongodmatchu.domain.notice.entity.Notice;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record AdminNoticeListItemResponse(
    Long id,
    String title,
    @Schema(description = "DRAFT/PUBLISHED — 고정 라벨은 status=PUBLISHED && pinned 로 FE 파생")
        String status,
    boolean pinned,
    long viewCount,
    @Schema(description = "작성일") OffsetDateTime createdAt) {

  public static AdminNoticeListItemResponse from(Notice notice) {
    return new AdminNoticeListItemResponse(
        notice.getId(),
        notice.getTitle(),
        notice.getStatus().name(),
        notice.isPinned(),
        notice.getViewCount(),
        TimeFormat.toResponse(notice.getCreatedAt()));
  }
}
