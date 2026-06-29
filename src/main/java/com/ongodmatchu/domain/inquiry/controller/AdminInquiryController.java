package com.ongodmatchu.domain.inquiry.controller;

import com.ongodmatchu.domain.admin.dto.SendNotificationRequest;
import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.inquiry.dto.AdminInquiryDetailResponse;
import com.ongodmatchu.domain.inquiry.dto.AdminInquiryListItemResponse;
import com.ongodmatchu.domain.inquiry.dto.InquiryStatsResponse;
import com.ongodmatchu.domain.inquiry.dto.InquiryStatusUpdateRequest;
import com.ongodmatchu.domain.inquiry.service.AdminInquiryService;
import com.ongodmatchu.domain.inquiry.service.InquiryFilter;
import com.ongodmatchu.domain.notification.dto.NotificationResponse;
import com.ongodmatchu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@Tag(name = "Admin Inquiry", description = "백오피스 문의사항 관리 (ADMIN/OWNER 전용)")
@RestController
@RequestMapping("/api/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

  private final AdminInquiryService adminInquiryService;

  @Operation(
      summary = "문의 목록",
      description = "filter=ALL/PENDING/IN_PROGRESS/DONE + 제목 검색. 접수 최신순. size 최대 50.")
  @GetMapping
  public ResponseEntity<ApiResponse<Page<AdminInquiryListItemResponse>>> getInquiries(
      @RequestParam(defaultValue = "ALL") InquiryFilter filter,
      @RequestParam(required = false) String query,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.ok(adminInquiryService.getInquiries(filter, query, pageable)));
  }

  @Operation(summary = "문의 통계", description = "전체/대기/처리중/완료 카운트.")
  @GetMapping("/stats")
  public ResponseEntity<ApiResponse<InquiryStatsResponse>> getStats() {
    return ResponseEntity.ok(ApiResponse.ok(adminInquiryService.getStats()));
  }

  @Operation(summary = "문의 상세", description = "제목/내용 + 문의 유저 + 보낸 답변. 없으면 404 `INQUIRY_NOT_FOUND`.")
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<AdminInquiryDetailResponse>> getInquiry(@PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.ok(adminInquiryService.getInquiry(id)));
  }

  @Operation(summary = "처리 상태 변경", description = "대기/처리중/완료 수동 전환. 답변 발송과 독립.")
  @PatchMapping("/{id}/status")
  public ResponseEntity<ApiResponse<AdminInquiryDetailResponse>> changeStatus(
      @PathVariable Long id, @Valid @RequestBody InquiryStatusUpdateRequest request) {
    return ResponseEntity.ok(
        ApiResponse.ok(adminInquiryService.changeStatus(id, request.status())));
  }

  @Operation(
      summary = "답변 발송",
      description = "문의 작성자에게 알림 발송 + 문의에 연결(여러 번 가능). 상태는 자동 전환하지 않음. 탈퇴자 대상은 거부.")
  @PostMapping("/{id}/answers")
  public ResponseEntity<ApiResponse<NotificationResponse>> answer(
      @AuthenticationPrincipal CustomUserDetails actor,
      @PathVariable Long id,
      @Valid @RequestBody SendNotificationRequest request) {
    return ResponseEntity.ok(
        ApiResponse.ok(adminInquiryService.answer(actor.getUser().getId(), id, request)));
  }
}
