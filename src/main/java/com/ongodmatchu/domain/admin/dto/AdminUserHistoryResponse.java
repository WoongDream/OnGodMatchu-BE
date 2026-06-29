package com.ongodmatchu.domain.admin.dto;

import com.ongodmatchu.domain.admin.entity.AdminUserChangeType;
import com.ongodmatchu.domain.admin.entity.AdminUserHistory;
import com.ongodmatchu.domain.notification.dto.NotificationResponse;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserHistoryResponse(
    Long id,
    UUID actorId,
    String actorNickname,
    String actorRole,
    @Schema(description = "변경 유형. 알림만 보낸 경우 null", nullable = true) String changeType,
    @Schema(nullable = true) String changeTypeLabel,
    @Schema(description = "변경 상세. 알림만 보낸 경우 null", nullable = true) String detail,
    @Schema(description = "관련 알림. 없으면 null", nullable = true) NotificationResponse notification,
    OffsetDateTime createdAt) {

  public static AdminUserHistoryResponse from(AdminUserHistory history) {
    AdminUserChangeType type = history.getChangeType();
    return new AdminUserHistoryResponse(
        history.getId(),
        history.getActor().getPublicId(),
        history.getActor().getNickname(),
        history.getActor().getRole().name(),
        type == null ? null : type.name(),
        type == null ? null : type.getLabel(),
        history.getDetail(),
        history.getRelatedNotification() == null
            ? null
            : NotificationResponse.from(history.getRelatedNotification()),
        TimeFormat.toResponse(history.getCreatedAt()));
  }
}
