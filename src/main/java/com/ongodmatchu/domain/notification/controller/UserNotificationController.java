package com.ongodmatchu.domain.notification.controller;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.notification.dto.NotificationResponse;
import com.ongodmatchu.domain.notification.service.UserNotificationService;
import com.ongodmatchu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "사용자 알림 수신")
@RestController
@RequestMapping("/api/users/me/notifications")
@RequiredArgsConstructor
public class UserNotificationController {

  private final UserNotificationService userNotificationService;

  @Operation(summary = "미확인 알림 목록", description = "로그인/폴링 시 modal 로 띄울 미확인 알림 (오래된 순).")
  @GetMapping("/pending")
  public ResponseEntity<ApiResponse<List<NotificationResponse>>> getPending(
      @AuthenticationPrincipal CustomUserDetails me) {
    return ResponseEntity.ok(
        ApiResponse.ok(userNotificationService.getPending(me.getUser().getId())));
  }

  @Operation(summary = "알림 확인 처리", description = "본인 알림만. 정지 사용자도 확인 가능.")
  @PostMapping("/{id}/read")
  public ResponseEntity<ApiResponse<Void>> markRead(
      @AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id) {
    userNotificationService.markRead(me.getUser().getId(), id);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}
