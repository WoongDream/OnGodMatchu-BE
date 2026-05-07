package com.ongodmatchu.infra.s3;

import java.util.Map;

/**
 * S3 presigned PUT URL 응답.
 *
 * <p>{@code requiredHeaders} 는 클라이언트가 {@code uploadUrl} 로 PUT 할 때 반드시 부착해야 하는 헤더 (서명에 포함된 것). 누락 시
 * S3 가 403 을 반환한다.
 */
public record PresignedUrlResponse(
    String uploadUrl, String key, long expiresIn, Map<String, String> requiredHeaders) {}
