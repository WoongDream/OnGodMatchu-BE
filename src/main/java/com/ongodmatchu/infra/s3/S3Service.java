package com.ongodmatchu.infra.s3;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class S3Service {

  private static final Duration PRESIGNED_URL_EXPIRY = Duration.ofMinutes(10);

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;

  @Value("${aws.s3.bucket}")
  private String bucket;

  @Value("${aws.region}")
  private String region;

  /** Presigned URL 발급. 클라이언트가 직접 S3에 업로드하므로 서버를 거치지 않아 비용·트래픽 절감. */
  public PresignedUrlResponse generatePresignedUrl(String folder, String originalFilename) {
    String key = buildKey(folder, originalFilename);

    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(PRESIGNED_URL_EXPIRY)
            .putObjectRequest(r -> r.bucket(bucket).key(key))
            .build();

    PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
    String uploadUrl = presignedRequest.url().toString();
    String fileUrl = buildFileUrl(key);

    return new PresignedUrlResponse(uploadUrl, fileUrl, key);
  }

  public void delete(String key) {
    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }

  private String buildKey(String folder, String originalFilename) {
    String ext = extractExtension(originalFilename);
    return folder + "/" + UUID.randomUUID() + ext;
  }

  private String buildFileUrl(String key) {
    return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
  }

  private String extractExtension(String filename) {
    int dotIndex = filename.lastIndexOf('.');
    return dotIndex >= 0 ? filename.substring(dotIndex) : "";
  }
}
