package com.ongodmatchu.domain.nickname.dto;

import com.ongodmatchu.domain.nickname.entity.ForbiddenNickname;
import com.ongodmatchu.global.util.TimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** 차단 닉네임 규칙 응답 (목록·단건 공용). */
public record ForbiddenNicknameResponse(
    Long id,
    @Schema(description = "표시용 원본 패턴") String value,
    @Schema(description = "정규화된 매칭 키 (Tier 0·1)") String normalizedValue,
    @Schema(description = "RESERVED/FORBIDDEN") String type,
    @Schema(description = "EXACT/PREFIX/CONTAINS") String matchType,
    @Schema(nullable = true) String reason,
    @Schema(description = "등록일") OffsetDateTime createdAt) {

  public static ForbiddenNicknameResponse from(ForbiddenNickname f) {
    return new ForbiddenNicknameResponse(
        f.getId(),
        f.getRawValue(),
        f.getNormalizedValue(),
        f.getType().name(),
        f.getMatchType().name(),
        f.getReason(),
        TimeFormat.toResponse(f.getCreatedAt()));
  }
}
