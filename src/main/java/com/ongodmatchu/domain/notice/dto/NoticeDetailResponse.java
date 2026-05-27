package com.ongodmatchu.domain.notice.dto;

import com.ongodmatchu.domain.notice.entity.Notice;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record NoticeDetailResponse(
    Long id,
    String title,
    @Schema(description = "본문 (Markdown)") String content,
    @Schema(description = "발행 시각 (ISO 8601 +09:00)") OffsetDateTime publishedAt) {

  public static NoticeDetailResponse from(Notice notice) {
    return new NoticeDetailResponse(
        notice.getId(),
        notice.getTitle(),
        notice.getContent(),
        TimeFormat.toResponse(notice.getPublishedAt()));
  }
}
