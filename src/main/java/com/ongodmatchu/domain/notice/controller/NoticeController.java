package com.ongodmatchu.domain.notice.controller;

import com.ongodmatchu.domain.notice.dto.NoticeDetailResponse;
import com.ongodmatchu.domain.notice.dto.NoticeListItemResponse;
import com.ongodmatchu.domain.notice.service.NoticeService;
import com.ongodmatchu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Notice", description = "공지사항 공개 조회. 게시 상태만 노출, 고정 공지 우선. 비로그인 열람 허용 (permitAll).")
public class NoticeController {

  private final NoticeService noticeService;

  @Operation(summary = "공지사항 목록", description = "게시된 공지만 반환. 고정 우선 + 최신 게시순. 디폴트 size=20 / max=50.")
  @GetMapping("/api/announcements")
  public ResponseEntity<ApiResponse<Page<NoticeListItemResponse>>> getAnnouncements(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(ApiResponse.ok(noticeService.getAnnouncements(pageable)));
  }

  @Operation(
      summary = "공지사항 단건 조회",
      description = "id 로 게시된 공지 단건 조회. 조회 시 조회수 +1. 없거나 미게시 → 404 `NOTICE_NOT_FOUND`.")
  @GetMapping("/api/announcements/{id}")
  public ResponseEntity<ApiResponse<NoticeDetailResponse>> getAnnouncementDetail(
      @PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.ok(noticeService.getAnnouncementDetail(id)));
  }
}
