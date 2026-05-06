package com.ongodmatchu.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeRequest(
    @NotBlank(message = "현재 비밀번호를 입력해주세요.") String currentPassword,
    @NotBlank(message = "새 비밀번호를 입력해주세요.") String newPassword) {}
