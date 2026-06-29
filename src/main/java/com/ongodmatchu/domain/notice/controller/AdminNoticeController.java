package com.ongodmatchu.domain.notice.controller;

import com.ongodmatchu.domain.notice.dto.AdminNoticeListItemResponse;
import com.ongodmatchu.domain.notice.dto.AdminNoticeResponse;
import com.ongodmatchu.domain.notice.dto.NoticeCreateRequest;
import com.ongodmatchu.domain.notice.dto.NoticeStatsResponse;
import com.ongodmatchu.domain.notice.dto.NoticeUpdateRequest;
import com.ongodmatchu.domain.notice.service.AdminNoticeService;
import com.ongodmatchu.domain.notice.service.NoticeFilter;
import com.ongodmatchu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Notice", description = "백오피스 공지 관리 (OWNER 전용)")
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

  private final AdminNoticeService adminNoticeService;

  @Operation(
      summary = "공지 목록",
      description = "filter=ALL/PINNED/PUBLISHED/DRAFT + 제목 검색. size 최대 50.")
  @GetMapping
  public ResponseEntity<ApiResponse<Page<AdminNoticeListItemResponse>>> getNotices(
      @RequestParam(defaultValue = "ALL") NoticeFilter filter,
      @RequestParam(required = false) String query,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.ok(adminNoticeService.getNotices(filter, query, pageable)));
  }

  @Operation(summary = "공지 통계", description = "전체/게시(미고정)/고정/임시저장 카운트.")
  @GetMapping("/stats")
  public ResponseEntity<ApiResponse<NoticeStatsResponse>> getStats() {
    return ResponseEntity.ok(ApiResponse.ok(adminNoticeService.getStats()));
  }

  @Operation(summary = "공지 단건", description = "상태 무관 단건 조회. 없으면 404 `NOTICE_NOT_FOUND`.")
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<AdminNoticeResponse>> getNotice(@PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.ok(adminNoticeService.getNotice(id)));
  }

  @Operation(summary = "공지 생성", description = "status 미지정 시 DRAFT.")
  @PostMapping
  public ResponseEntity<ApiResponse<AdminNoticeResponse>> create(
      @Valid @RequestBody NoticeCreateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(adminNoticeService.create(request)));
  }

  @Operation(summary = "공지 수정", description = "제목·내용·상태·고정 부분 수정. null 필드는 미변경.")
  @PatchMapping("/{id}")
  public ResponseEntity<ApiResponse<AdminNoticeResponse>> update(
      @PathVariable Long id, @Valid @RequestBody NoticeUpdateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(adminNoticeService.update(id, request)));
  }

  @Operation(summary = "공지 삭제")
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
    adminNoticeService.delete(id);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}
