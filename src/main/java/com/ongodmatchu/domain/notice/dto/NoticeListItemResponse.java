package com.ongodmatchu.domain.notice.dto;

import com.ongodmatchu.domain.notice.entity.Notice;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record NoticeListItemResponse(
    Long id,
    String title,
    @Schema(description = "발행 시각 (ISO 8601 +09:00)") OffsetDateTime publishedAt) {

  public static NoticeListItemResponse from(Notice notice) {
    return new NoticeListItemResponse(
        notice.getId(), notice.getTitle(), TimeFormat.toResponse(notice.getPublishedAt()));
  }
}
