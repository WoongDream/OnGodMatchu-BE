package com.ongodmatchu.domain.visit.controller;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.visit.dto.VisitCreateRequest;
import com.ongodmatchu.domain.visit.service.VisitLogService;
import com.ongodmatchu.global.response.ApiResponse;
import com.ongodmatchu.global.web.AnonIdCookieFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
@Tag(name = "Visit", description = "SPA 페이지 방문 트래킹 (DAU 측정용). FE 가 라우팅 변경 시점에 호출")
public class VisitController {

  private final VisitLogService visitLogService;

  @Operation(
      summary = "페이지 방문 기록",
      description =
          "anon_id 쿠키 자동 동봉 + 로그인 시 user_id 함께 기록. 비동기 적재 — 응답은 즉시 반환. "
              + "트래킹 대상 path 화이트리스트는 FE 책임 (API/정적 리소스 제외).")
  @PostMapping
  public ResponseEntity<ApiResponse<Void>> recordVisit(
      @Valid @RequestBody VisitCreateRequest request,
      HttpServletRequest httpRequest,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    String anonId = (String) httpRequest.getAttribute(AnonIdCookieFilter.REQUEST_ATTRIBUTE);
    Long userId = userDetails != null ? userDetails.getUser().getId() : null;
    visitLogService.recordVisit(anonId, userId, request.path());
    return ResponseEntity.ok(ApiResponse.ok());
  }
}
