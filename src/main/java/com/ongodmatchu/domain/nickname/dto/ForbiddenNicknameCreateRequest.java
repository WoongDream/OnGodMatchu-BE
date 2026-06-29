package com.ongodmatchu.domain.nickname.dto;

import com.ongodmatchu.domain.nickname.entity.ForbiddenNicknameType;
import com.ongodmatchu.domain.nickname.entity.NicknameMatchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 차단 닉네임 등록. 유형/매칭 기본값(예약→PREFIX, 금지→CONTAINS)은 FE 폼에서 세팅해 전송. */
public record ForbiddenNicknameCreateRequest(
    @NotBlank @Size(max = 100) String value,
    @NotNull ForbiddenNicknameType type,
    @NotNull NicknameMatchType matchType,
    @Size(max = 200) String reason) {}
