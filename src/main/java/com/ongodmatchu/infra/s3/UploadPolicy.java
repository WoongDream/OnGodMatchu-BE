package com.ongodmatchu.infra.s3;

import java.util.Map;
import java.util.Set;

public final class UploadPolicy {

  public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

  public static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");

  private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE =
      Map.of(
          "image/jpeg", ".jpg",
          "image/png", ".png",
          "image/webp", ".webp");

  public static final String QUIZ_IMAGES_PREFIX = "quiz-images";

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
}
