package com.ongodmatchu.infra.s3;

import jakarta.validation.constraints.NotBlank;

public record PresignedUrlRequest(
    @NotBlank(message = "파일명을 입력해주세요.") String filename,
    @NotBlank(message = "폴더를 입력해주세요.") String folder) {}
