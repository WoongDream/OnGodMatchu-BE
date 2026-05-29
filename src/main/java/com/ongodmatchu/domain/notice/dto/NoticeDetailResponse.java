package com.ongodmatchu.domain.notice.dto;

import com.ongodmatchu.domain.notice.document.NoticeDocument;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record NoticeDetailResponse(
    String slug,
    String title,
    @Schema(description = "본문 (Markdown)") String content,
    @Schema(description = "발행 시각 (ISO 8601 +09:00, 자정 KST)") OffsetDateTime publishedAt) {

  public static NoticeDetailResponse from(NoticeDocument doc) {
    return new NoticeDetailResponse(
        doc.slug(),
        doc.title(),
        doc.content(),
        TimeFormat.toResponse(doc.publishedAt().atStartOfDay()));
  }
}
