package com.ongodmatchu.domain.notice.dto;

import com.ongodmatchu.domain.notice.entity.Notice;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record AdminNoticeResponse(
    Long id,
    String title,
    @Schema(description = "본문 (Markdown)") String content,
    String status,
    boolean pinned,
    long viewCount,
    @Schema(description = "게시 시각. 미게시면 null", nullable = true) OffsetDateTime publishedAt,
    @Schema(description = "작성일") OffsetDateTime createdAt,
    @Schema(nullable = true) OffsetDateTime updatedAt) {

  public static AdminNoticeResponse from(Notice notice) {
    return new AdminNoticeResponse(
        notice.getId(),
        notice.getTitle(),
        notice.getContent(),
        notice.getStatus().name(),
        notice.isPinned(),
        notice.getViewCount(),
        TimeFormat.toResponse(notice.getPublishedAt()),
        TimeFormat.toResponse(notice.getCreatedAt()),
        TimeFormat.toResponse(notice.getUpdatedAt()));
  }
}
