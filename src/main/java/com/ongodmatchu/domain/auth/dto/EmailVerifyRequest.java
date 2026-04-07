package com.ongodmatchu.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailVerifyRequest(
    @Email @NotBlank(message = "이메일을 입력해주세요.") String email,
    @NotBlank(message = "인증 코드를 입력해주세요.") String code) {}
