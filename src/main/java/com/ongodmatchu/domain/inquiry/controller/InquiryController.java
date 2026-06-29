package com.ongodmatchu.domain.inquiry.controller;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.inquiry.dto.InquiryCreateRequest;
import com.ongodmatchu.domain.inquiry.dto.InquiryResponse;
import com.ongodmatchu.domain.inquiry.service.InquiryService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inquiry", description = "문의하기 (접수 + 본인 문의 조회)")
@RestController
@RequiredArgsConstructor
public class InquiryController {

  private final InquiryService inquiryService;

  @Operation(summary = "문의 접수", description = "제목/내용으로 문의 접수. 로그인 필요, 정지 사용자도 허용.")
  @PostMapping("/api/inquiries")
  public ResponseEntity<ApiResponse<InquiryResponse>> create(
      @AuthenticationPrincipal CustomUserDetails me,
      @Valid @RequestBody InquiryCreateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(inquiryService.create(me.getUser().getId(), request)));
  }

  @Operation(summary = "본인 문의 목록", description = "접수한 문의 카드(최신순) + 받은 답변(연결 알림). size 최대 50.")
  @GetMapping("/api/users/me/inquiries")
  public ResponseEntity<ApiResponse<Page<InquiryResponse>>> getMyInquiries(
      @AuthenticationPrincipal CustomUserDetails me,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.ok(inquiryService.getMyInquiries(me.getUser().getId(), pageable)));
  }
}
