package com.ongodmatchu.infra.s3;

import java.util.Map;
import java.util.Set;

public final class UploadPolicy {

  public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

  /** 프로필 이미지 전용 한도. 표시 영역이 작아 퀴즈 이미지보다 빡빡하게 둔다. */
  public static final long PROFILE_MAX_SIZE_BYTES = 3L * 1024 * 1024;

  public static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");

  private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE =
      Map.of(
          "image/jpeg", ".jpg",
          "image/png", ".png",
          "image/webp", ".webp");

  public static final String QUIZ_IMAGES_PREFIX = "quiz-images";

  public static final String PROFILE_IMAGES_PREFIX = "profile-images";

  /** PUT 시 부착되는 태그. 라이프사이클 룰 (1day expire) 의 필터 키와 일치해야 한다. */
  public static final String PENDING_TAG_KEY = "status";

  public static final String PENDING_TAG_VALUE = "pending";

  public static final String PENDING_TAGGING_HEADER = PENDING_TAG_KEY + "=" + PENDING_TAG_VALUE;

  private UploadPolicy() {}

  public static String extensionFor(String contentType) {
    return EXTENSION_BY_CONTENT_TYPE.get(contentType);
  }

  public static boolean isAllowedContentType(String contentType) {
    return contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType);
  }

  public static boolean isAllowedSize(Long sizeBytes) {
    return sizeBytes == null || sizeBytes <= MAX_SIZE_BYTES;
  }

  public static boolean isAllowedProfileSize(Long sizeBytes) {
    return sizeBytes == null || sizeBytes <= PROFILE_MAX_SIZE_BYTES;
  }
}
