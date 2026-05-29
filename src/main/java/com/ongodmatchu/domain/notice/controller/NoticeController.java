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
@Tag(
    name = "Notice",
    description =
        "공지사항 조회. 본문은 Markdown 으로 `src/main/resources/notices/announcements/` 의 정적 파일에서 로드된다. "
            + "식별자는 파일명 기반 slug (예: `service-open`). 비로그인 열람 허용 (permitAll). "
            + "릴리즈 노트는 FE 정적 markdown 으로 옮겨졌으며 BE 엔드포인트 없음.")
public class NoticeController {

  private final NoticeService noticeService;

  @Operation(
      summary = "공지사항 목록",
      description =
          "공지사항을 최신순으로 반환. 디폴트 size=20 / max=50. 정렬은 `publishedAt DESC, slug DESC` 강제. "
              + "에러 없음 — 빈 페이지는 정상 응답.")
  @GetMapping("/api/announcements")
  public ResponseEntity<ApiResponse<Page<NoticeListItemResponse>>> getAnnouncements(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(ApiResponse.ok(noticeService.getAnnouncements(pageable)));
  }

  @Operation(
      summary = "공지사항 단건 조회",
      description = "slug 로 공지사항 단건 조회. 존재하지 않음 → 404 `NOTICE_NOT_FOUND`.")
  @GetMapping("/api/announcements/{slug}")
  public ResponseEntity<ApiResponse<NoticeDetailResponse>> getAnnouncementDetail(
      @PathVariable String slug) {
    return ResponseEntity.ok(ApiResponse.ok(noticeService.getAnnouncementDetail(slug)));
  }
}
