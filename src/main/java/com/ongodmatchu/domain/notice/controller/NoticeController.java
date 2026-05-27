package com.ongodmatchu.domain.notice.controller;

import com.ongodmatchu.domain.notice.dto.NoticeDetailResponse;
import com.ongodmatchu.domain.notice.dto.NoticeListItemResponse;
import com.ongodmatchu.domain.notice.entity.NoticeType;
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
        "공지사항(`ANNOUNCEMENT`) / 릴리즈 노트(`RELEASE_NOTE`) 조회. content 는 Markdown. "
            + "발행되지 않은(`publishedAt IS NULL`) 항목은 노출되지 않음. 비로그인 열람 허용 (permitAll).")
public class NoticeController {

  private final NoticeService noticeService;

  @Operation(
      summary = "공지사항 목록",
      description =
          "발행된 공지사항을 최신순으로 반환. 디폴트 size=20 / max=50. 정렬은 `publishedAt DESC, id DESC` 강제. "
              + "에러 없음 — 빈 페이지는 정상 응답.")
  @GetMapping("/api/announcements")
  public ResponseEntity<ApiResponse<Page<NoticeListItemResponse>>> getAnnouncements(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.ok(noticeService.getList(NoticeType.ANNOUNCEMENT, pageable)));
  }

  @Operation(
      summary = "공지사항 단건 조회",
      description = "발행된 공지사항만 조회. 미발행 / 존재하지 않음 → 404 `NOTICE_NOT_FOUND`.")
  @GetMapping("/api/announcements/{noticeId}")
  public ResponseEntity<ApiResponse<NoticeDetailResponse>> getAnnouncementDetail(
      @PathVariable Long noticeId) {
    return ResponseEntity.ok(
        ApiResponse.ok(noticeService.getDetail(NoticeType.ANNOUNCEMENT, noticeId)));
  }

  @Operation(
      summary = "릴리즈 노트 목록",
      description =
          "발행된 릴리즈 노트를 최신순으로 반환. 디폴트 size=20 / max=50. 정렬은 `publishedAt DESC, id DESC` 강제.")
  @GetMapping("/api/release-notes")
  public ResponseEntity<ApiResponse<Page<NoticeListItemResponse>>> getReleaseNotes(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.ok(noticeService.getList(NoticeType.RELEASE_NOTE, pageable)));
  }

  @Operation(
      summary = "릴리즈 노트 단건 조회",
      description = "발행된 릴리즈 노트만 조회. 미발행 / 존재하지 않음 → 404 `NOTICE_NOT_FOUND`.")
  @GetMapping("/api/release-notes/{noticeId}")
  public ResponseEntity<ApiResponse<NoticeDetailResponse>> getReleaseNoteDetail(
      @PathVariable Long noticeId) {
    return ResponseEntity.ok(
        ApiResponse.ok(noticeService.getDetail(NoticeType.RELEASE_NOTE, noticeId)));
  }
}
