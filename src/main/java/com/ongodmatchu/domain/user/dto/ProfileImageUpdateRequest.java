package com.ongodmatchu.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfileImageUpdateRequest(@NotBlank(message = "key 를 입력해주세요.") String key) {}
