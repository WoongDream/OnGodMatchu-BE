package com.ongodmatchu.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @Email(message = "이메일 형식이 올바르지 않습니다.") @NotBlank(message = "이메일을 입력해주세요.") String email,
    @NotBlank(message = "닉네임을 입력해주세요.") @Size(min = 2, max = 20, message = "닉네임은 2~20자 사이여야 합니다.")
        String nickname,
    @NotBlank(message = "비밀번호를 입력해주세요.") @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password) {}
