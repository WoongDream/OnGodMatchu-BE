package com.ongodmatchu.domain.notification.dto;

import com.ongodmatchu.domain.notification.entity.UserNotification;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record NotificationResponse(
    Long id,
    @Schema(description = "INFO/WARNING") String type,
    @Schema(description = "안내/경고") String typeLabel,
    String title,
    String content,
    @Schema(description = "발신자 표시명") String senderLabel,
    OffsetDateTime createdAt) {

  private static final String SENDER_LABEL = "운영팀";

  public static NotificationResponse from(UserNotification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getType().name(),
        notification.getType().getLabel(),
        notification.getTitle(),
        notification.getContent(),
        SENDER_LABEL,
        TimeFormat.toResponse(notification.getCreatedAt()));
  }
}
