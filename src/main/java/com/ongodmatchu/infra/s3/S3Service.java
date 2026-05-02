package com.ongodmatchu.infra.s3;

import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class S3Service {

  static final Duration UPLOAD_URL_EXPIRY = Duration.ofMinutes(10);
  static final Duration VIEW_URL_EXPIRY = Duration.ofHours(1);

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final UserRepository userRepository;
  private final UploadMetaRepository uploadMetaRepository;

  @Value("${aws.s3.bucket}")
  private String bucket;

  @Transactional
  public PresignedUrlResponse generateUploadUrl(Long userId, PresignedUrlRequest request) {
    if (!UploadPolicy.isAllowedContentType(request.contentType())) {
      throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
    }
    if (!UploadPolicy.isAllowedSize(request.sizeBytes())) {
      throw new BusinessException(ErrorCode.INVALID_FILE_SIZE);
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    String key = buildQuizImageKey(user.getPublicId(), request.contentType());

    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(UPLOAD_URL_EXPIRY)
            .putObjectRequest(
                r ->
                    r.bucket(bucket)
                        .key(key)
                        .contentType(request.contentType())
                        .tagging(UploadPolicy.PENDING_TAGGING_HEADER))
            .build();
    PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

    uploadMetaRepository.save(
        UploadMeta.builder()
            .user(user)
            .s3Key(key)
            .originalName(request.filename())
            .contentType(request.contentType())
            .sizeBytes(request.sizeBytes())
            .build());

    return new PresignedUrlResponse(presigned.url().toString(), key, UPLOAD_URL_EXPIRY.toSeconds());
  }

  public ViewUrlResponse generateViewUrl(String key) {
    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(VIEW_URL_EXPIRY)
            .getObjectRequest(r -> r.bucket(bucket).key(key))
            .build();
    PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
    Instant expiresAt = Instant.now().plus(VIEW_URL_EXPIRY);
    return new ViewUrlResponse(
        presigned.url().toString(), key, VIEW_URL_EXPIRY.toSeconds(), expiresAt);
  }

  public Map<String, String> batchPresignViewUrls(List<String> keys) {
    Map<String, String> result = new LinkedHashMap<>();
    for (String key : keys) {
      if (key == null) continue;
      result.put(key, generateViewUrl(key).viewUrl());
    }
    return result;
  }

  @Transactional
  public void completeUpload(Long userId, String key) {
    UploadMeta meta =
        uploadMetaRepository
            .findByS3Key(key)
            .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_NOT_FOUND));
    if (!meta.getUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.UPLOAD_FORBIDDEN);
    }

    HeadObjectResponse head;
    try {
      head = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
    } catch (NoSuchKeyException e) {
      throw new BusinessException(ErrorCode.UPLOAD_VERIFICATION_FAILED);
    }

    if (!UploadPolicy.isAllowedContentType(head.contentType())) {
      throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
    }
    if (!UploadPolicy.isAllowedSize(head.contentLength())) {
      throw new BusinessException(ErrorCode.INVALID_FILE_SIZE);
    }

    // 태그 제거가 실패하면 DB 도 PENDING 으로 남아 사용자가 /complete 재시도 가능.
    s3Client.deleteObjectTagging(
        DeleteObjectTaggingRequest.builder().bucket(bucket).key(key).build());

    meta.markCompleted(head.contentLength());
  }

  public void verifyKeyOwnedAndCompleted(Long userId, String key) {
    if (key == null) return;
    UploadMeta meta =
        uploadMetaRepository
            .findByS3Key(key)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_UPLOAD_KEY));
    if (!meta.getUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.UPLOAD_FORBIDDEN);
    }
    if (meta.getStatus() != UploadStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.UPLOAD_VERIFICATION_FAILED);
    }
  }

  public void delete(String key) {
    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }

  private String buildQuizImageKey(UUID userPublicId, String contentType) {
    String ext = UploadPolicy.extensionFor(contentType);
    return UploadPolicy.QUIZ_IMAGES_PREFIX + "/" + userPublicId + "/" + UUID.randomUUID() + ext;
  }
}
