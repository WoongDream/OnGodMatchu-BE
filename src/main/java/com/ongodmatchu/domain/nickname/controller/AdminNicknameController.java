package com.ongodmatchu.domain.nickname.controller;

import com.ongodmatchu.domain.nickname.dto.ForbiddenNicknameCreateRequest;
import com.ongodmatchu.domain.nickname.dto.ForbiddenNicknameResponse;
import com.ongodmatchu.domain.nickname.dto.ForbiddenNicknameUpdateRequest;
import com.ongodmatchu.domain.nickname.dto.NicknameRuleStatsResponse;
import com.ongodmatchu.domain.nickname.service.ForbiddenNicknameService;
import com.ongodmatchu.domain.nickname.service.NicknameRuleFilter;
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

@Tag(name = "Admin Nickname", description = "백오피스 차단 닉네임 관리 (ADMIN/OWNER 전용)")
@RestController
@RequestMapping("/api/admin/nicknames")
@RequiredArgsConstructor
public class AdminNicknameController {

  private final ForbiddenNicknameService forbiddenNicknameService;

  @Operation(
      summary = "차단 닉네임 목록",
      description = "filter=ALL/FORBIDDEN/RESERVED + 패턴 검색(원본·정규화값). 최신 등록순. size 최대 50.")
  @GetMapping
  public ResponseEntity<ApiResponse<Page<ForbiddenNicknameResponse>>> getRules(
      @RequestParam(defaultValue = "ALL") NicknameRuleFilter filter,
      @RequestParam(required = false) String query,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.ok(forbiddenNicknameService.getRules(filter, query, pageable)));
  }

  @Operation(summary = "차단 닉네임 통계", description = "전체 규칙 / 금지 / 예약 카운트.")
  @GetMapping("/stats")
  public ResponseEntity<ApiResponse<NicknameRuleStatsResponse>> getStats() {
    return ResponseEntity.ok(ApiResponse.ok(forbiddenNicknameService.getStats()));
  }

  @Operation(summary = "차단 닉네임 단건", description = "없으면 404 `FORBIDDEN_NICKNAME_NOT_FOUND`.")
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ForbiddenNicknameResponse>> getRule(@PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.ok(forbiddenNicknameService.getRule(id)));
  }

  @Operation(
      summary = "차단 닉네임 등록",
      description = "정규화값+매칭 중복 시 409 `FORBIDDEN_NICKNAME_DUPLICATE`. 정규화 후 빈 패턴은 400.")
  @PostMapping
  public ResponseEntity<ApiResponse<ForbiddenNicknameResponse>> create(
      @Valid @RequestBody ForbiddenNicknameCreateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(forbiddenNicknameService.create(request)));
  }

  @Operation(summary = "차단 닉네임 수정", description = "값/유형/매칭/사유 저장. 정규화값+매칭 중복 시 409.")
  @PatchMapping("/{id}")
  public ResponseEntity<ApiResponse<ForbiddenNicknameResponse>> update(
      @PathVariable Long id, @Valid @RequestBody ForbiddenNicknameUpdateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(forbiddenNicknameService.update(id, request)));
  }

  @Operation(summary = "차단 닉네임 삭제")
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
    forbiddenNicknameService.delete(id);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}
