package com.ongodmatchu.infra.s3;

import jakarta.validation.constraints.NotBlank;

public record UploadCompleteRequest(@NotBlank(message = "key 를 입력해주세요.") String key) {}
