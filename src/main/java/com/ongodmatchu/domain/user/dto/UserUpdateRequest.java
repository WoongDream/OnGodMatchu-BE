package com.ongodmatchu.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @NotBlank(message = "닉네임을 입력해주세요.") @Size(min = 2, max = 20, message = "닉네임은 2~20자 사이여야 합니다.")
        String nickname) {}
