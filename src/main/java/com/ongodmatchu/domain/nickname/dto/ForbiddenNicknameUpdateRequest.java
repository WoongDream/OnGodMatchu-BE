package com.ongodmatchu.domain.nickname.dto;

import com.ongodmatchu.domain.nickname.entity.ForbiddenNicknameType;
import com.ongodmatchu.domain.nickname.entity.NicknameMatchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 차단 닉네임 수정 — 수정 화면 폼 전체 저장(값/유형/매칭 필수, 사유 선택). */
public record ForbiddenNicknameUpdateRequest(
    @NotBlank @Size(max = 100) String value,
    @NotNull ForbiddenNicknameType type,
    @NotNull NicknameMatchType matchType,
    @Size(max = 200) String reason) {}
