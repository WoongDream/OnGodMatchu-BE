package com.ongodmatchu.global.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;

/**
 * 이미지 크롭/변환 파라미터(transform) 저장 정책. transform 은 FE 소유 opaque JSON 이라 BE 는 의미를 해석하지 않고, 유효 JSON(이미
 * 파싱됨) + 크기 cap 만 검증한 뒤 jsonb 컬럼에 원문 그대로 저장한다.
 */
public final class ImageTransformPolicy {

  /** transform JSON 최대 크기 (bytes). 크롭/회전/반전 메타는 수백 바이트 수준이라 2KB 면 충분. */
  public static final int MAX_BYTES = 2048;

  private ImageTransformPolicy() {}

  /** JsonNode → 저장용 JSON 문자열. null/JSON null 이면 null. 2KB 초과 시 {@link ErrorCode#INVALID_INPUT}. */
  public static String toStoredJson(JsonNode transform) {
    if (transform == null || transform.isNull()) {
      return null;
    }
    String json = transform.toString();
    if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
    return json;
  }
}
