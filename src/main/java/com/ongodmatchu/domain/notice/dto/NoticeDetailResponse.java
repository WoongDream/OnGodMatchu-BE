package com.ongodmatchu.domain.notice.dto;

import com.ongodmatchu.domain.notice.entity.Notice;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record NoticeDetailResponse(
    Long id,
    String title,
    @Schema(description = "본문 (Markdown)") String content,
    boolean pinned,
    @Schema(description = "게시 시각 (ISO 8601 +09:00)") OffsetDateTime publishedAt,
    @Schema(description = "최종 수정 시각. 작성/변경일 표시용", nullable = true) OffsetDateTime updatedAt,
    long viewCount) {

  public static NoticeDetailResponse from(Notice notice) {
    return new NoticeDetailResponse(
        notice.getId(),
        notice.getTitle(),
        notice.getContent(),
        notice.isPinned(),
        TimeFormat.toResponse(notice.getPublishedAt()),
        TimeFormat.toResponse(notice.getUpdatedAt()),
        notice.getViewCount());
  }
}
