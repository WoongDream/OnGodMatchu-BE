package com.ongodmatchu.domain.user.dto;

import jakarta.validation.constraints.Size;

/** PATCH 의미 — null 인 필드는 변경 없음. nickname 은 빈 문자열 금지(정책에서 차단). */
public record UserUpdateRequest(
    @Size(min = 2, max = 10, message = "닉네임은 2~10자 사이여야 합니다.") String nickname,
    @Size(max = 40, message = "한 줄 소개는 40자 이하여야 합니다.") String bio,
    Boolean isProfilePublic) {}
