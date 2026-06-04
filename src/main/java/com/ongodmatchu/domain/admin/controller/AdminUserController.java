package com.ongodmatchu.domain.admin.controller;

import com.ongodmatchu.domain.admin.dto.AdminUserDetailResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserHistoryResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserSummaryResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserUpdateRequest;
import com.ongodmatchu.domain.admin.dto.ChangeRoleRequest;
import com.ongodmatchu.domain.admin.dto.MonthlyUserStatResponse;
import com.ongodmatchu.domain.admin.dto.SendNotificationRequest;
import com.ongodmatchu.domain.admin.dto.SuspendRequest;
import com.ongodmatchu.domain.admin.service.AdminUserService;
import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.user.entity.Role;
import com.ongodmatchu.domain.user.entity.UserStatus;
import com.ongodmatchu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin", description = "백오피스 사용자 관리 (ADMIN/OWNER 전용)")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

  private final AdminUserService adminUserService;

  @Operation(
      summary = "사용자 목록",
      description = "status 미지정 시 탈퇴자 숨김(활성만). status/role/query 필터. size 최대 50.")
  @GetMapping
  public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getUsers(
      @RequestParam(required = false) UserStatus status,
      @RequestParam(required = false) Role role,
      @RequestParam(required = false) String query,
      @PageableDefault(size = 20) Pageable pageable) {
    Page<AdminUserResponse> page =
        adminUserService.getUsers(status == null ? null : status.name(), role, query, pageable);
    return ResponseEntity.ok(ApiResponse.ok(page));
  }

  @Operation(summary = "사용자 통계 요약", description = "조회 화면 상단 카드 — 전체/역할별/정지/이번달 신규.")
  @GetMapping("/stats/summary")
  public ResponseEntity<ApiResponse<AdminUserSummaryResponse>> getSummary() {
    return ResponseEntity.ok(ApiResponse.ok(adminUserService.getSummary()));
  }

  @Operation(
      summary = "월별 유저 추이",
      description = "오래된 달 → 최신 달 순. 누적/신규/이탈(탈퇴). months 기본 6, 최대 24.")
  @GetMapping("/stats/monthly")
  public ResponseEntity<ApiResponse<List<MonthlyUserStatResponse>>> getMonthlyStats(
      @RequestParam(defaultValue = "6") int months) {
    return ResponseEntity.ok(ApiResponse.ok(adminUserService.getMonthlyStats(months)));
  }

  @Operation(
      summary = "사용자 상세",
      description = "프로필 + 역할/상태 + 플레이 기록 + 만든 퀴즈 통계. 탈퇴/시스템은 USER_NOT_FOUND.")
  @GetMapping("/{publicId}")
  public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(
      @PathVariable UUID publicId) {
    return ResponseEntity.ok(ApiResponse.ok(adminUserService.getUserDetail(publicId)));
  }

  @Operation(
      summary = "사용자 정보 수정 + 선택적 알림 발송",
      description =
          "변경 항목만 채워 보냄(역할/정지/초기화). notification 이 있으면 함께 발송(알림 보내고 수정 저장), 없으면 알림 없이 저장. 관리 이력 1행 기록.")
  @PatchMapping("/{publicId}")
  public ResponseEntity<ApiResponse<AdminUserResponse>> updateUser(
      @AuthenticationPrincipal CustomUserDetails actor,
      @PathVariable UUID publicId,
      @Valid @RequestBody AdminUserUpdateRequest request) {
    return ResponseEntity.ok(
        ApiResponse.ok(adminUserService.updateUser(actor.getUser().getId(), publicId, request)));
  }

  @Operation(summary = "알림만 발송", description = "변경 없이 알림만 전송 (조회 리스트 '알림 보내기'). 탈퇴자 대상은 거부.")
  @PostMapping("/{publicId}/notifications")
  public ResponseEntity<ApiResponse<AdminUserResponse>> sendNotification(
      @AuthenticationPrincipal CustomUserDetails actor,
      @PathVariable UUID publicId,
      @Valid @RequestBody SendNotificationRequest request) {
    return ResponseEntity.ok(
        ApiResponse.ok(
            adminUserService.sendNotification(actor.getUser().getId(), publicId, request)));
  }

  @Operation(summary = "관리 이력", description = "수정 내용 + 관련 알림 페어, 최신순. size 최대 50.")
  @GetMapping("/{publicId}/histories")
  public ResponseEntity<ApiResponse<Page<AdminUserHistoryResponse>>> getHistories(
      @PathVariable UUID publicId, @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(ApiResponse.ok(adminUserService.getHistories(publicId, pageable)));
  }

  @Operation(
      summary = "사용자 정지(N일)",
      description =
          "ADMIN은 USER만, OWNER는 USER/ADMIN. OWNER 대상·탈퇴자·본인은 거부 (ADMIN_FORBIDDEN/ADMIN_TARGET_INVALID).")
  @PostMapping("/{publicId}/suspend")
  public ResponseEntity<ApiResponse<AdminUserResponse>> suspend(
      @AuthenticationPrincipal CustomUserDetails actor,
      @PathVariable UUID publicId,
      @Valid @RequestBody SuspendRequest request) {
    return ResponseEntity.ok(
        ApiResponse.ok(
            adminUserService.suspend(actor.getUser().getId(), publicId, request.days())));
  }

  @Operation(summary = "정지 해제")
  @PostMapping("/{publicId}/unsuspend")
  public ResponseEntity<ApiResponse<AdminUserResponse>> unsuspend(
      @AuthenticationPrincipal CustomUserDetails actor, @PathVariable UUID publicId) {
    return ResponseEntity.ok(
        ApiResponse.ok(adminUserService.unsuspend(actor.getUser().getId(), publicId)));
  }

  @Operation(
      summary = "권한 변경(임명/해임)",
      description = "OWNER만 USER↔ADMIN 변경 가능. ADMIN은 본인 자가 사임(ADMIN→USER)만. OWNER 부여/대상은 거부.")
  @PatchMapping("/{publicId}/role")
  public ResponseEntity<ApiResponse<AdminUserResponse>> changeRole(
      @AuthenticationPrincipal CustomUserDetails actor,
      @PathVariable UUID publicId,
      @Valid @RequestBody ChangeRoleRequest request) {
    return ResponseEntity.ok(
        ApiResponse.ok(
            adminUserService.changeRole(actor.getUser().getId(), publicId, request.role())));
  }
}
