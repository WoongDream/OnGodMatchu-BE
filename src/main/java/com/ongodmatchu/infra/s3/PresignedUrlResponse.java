package com.ongodmatchu.infra.s3;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * S3 presigned PUT URL 응답.
 *
 * <p>{@code requiredHeaders} 는 클라이언트가 {@code uploadUrl} 로 PUT 할 때 반드시 부착해야 하는 헤더 (서명에 포함된 것). 누락 시
 * S3 가 403 을 반환한다.
 */
@Schema(description = "S3 presigned PUT URL 응답 — uploadUrl 로 PUT 할 때 requiredHeaders 모두 부착 필수")
public record PresignedUrlResponse(
    @Schema(description = "S3 presigned PUT URL. TTL 10분") String uploadUrl,
    @Schema(description = "S3 객체 key — /complete 단계에서 BE 에 다시 전달") String key,
    @Schema(description = "URL 만료까지 남은 초") long expiresIn,
    @Schema(
            description =
                "PUT 시 부착해야 할 헤더 맵. 누락 시 S3 403. 보통 Content-Type + x-amz-tagging:status=pending")
        Map<String, String> requiredHeaders) {}
