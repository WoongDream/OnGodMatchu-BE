package com.ongodmatchu.infra.s3;

public record PresignedUrlResponse(String uploadUrl, String key, long expiresIn) {}
