package com.ongodmatchu.domain.admin.dto;

import com.ongodmatchu.domain.notification.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 관리자가 사용자에게 보낼 알림 입력. */
public record SendNotificationRequest(
    @NotNull NotificationType type,
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 2000) String content) {}
