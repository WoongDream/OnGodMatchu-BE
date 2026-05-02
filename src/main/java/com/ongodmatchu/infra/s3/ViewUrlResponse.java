package com.ongodmatchu.infra.s3;

import java.time.Instant;

public record ViewUrlResponse(String viewUrl, String key, long expiresIn, Instant expiresAt) {}
