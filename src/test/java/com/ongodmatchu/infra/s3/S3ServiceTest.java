package com.ongodmatchu.infra.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

  @InjectMocks private S3Service s3Service;
  @Mock private S3Client s3Client;
  @Mock private S3Presigner s3Presigner;
  @Mock private UserRepository userRepository;
  @Mock private UploadMetaRepository uploadMetaRepository;
  @Mock private PresignedPutObjectRequest presignedPutObjectRequest;
  @Mock private PresignedGetObjectRequest presignedGetObjectRequest;

  private static final UUID USER_PUBLIC_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(s3Service, "bucket", "test-bucket");
  }

  private User testUser() {
    User user =
        User.builder()
            .email("user@example.com")
            .nickname("작성자")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    ReflectionTestUtils.setField(user, "publicId", USER_PUBLIC_ID);
    return user;
  }

  @Test
  @DisplayName("PUT presigned URL 발급 — 허용 contentType / size 통과 시 PENDING 메타 저장")
  void generateUploadUrl_success() throws MalformedURLException {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .willReturn(presignedPutObjectRequest);
    given(presignedPutObjectRequest.url())
        .willReturn(new URL("https://test-bucket.s3.ap-northeast-2.amazonaws.com/upload"));

    PresignedUrlResponse response =
        s3Service.generateUploadUrl(1L, new PresignedUrlRequest("photo.jpg", "image/jpeg", 1024L));

    assertThat(response.key()).startsWith("quiz-images/" + USER_PUBLIC_ID + "/");
    assertThat(response.key()).endsWith(".jpg");
    assertThat(response.expiresIn()).isEqualTo(600L);
    ArgumentCaptor<UploadMeta> captor = ArgumentCaptor.forClass(UploadMeta.class);
    then(uploadMetaRepository).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(UploadStatus.PENDING);
    assertThat(captor.getValue().getContentType()).isEqualTo("image/jpeg");
  }

  @Test
  @DisplayName("PUT presigned — 허용되지 않은 contentType 은 INVALID_FILE_TYPE")
  void generateUploadUrl_invalidContentType() {
    assertThatThrownBy(
            () ->
                s3Service.generateUploadUrl(
                    1L, new PresignedUrlRequest("photo.gif", "image/gif", 1024L)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_FILE_TYPE);

    then(uploadMetaRepository).should(times(0)).save(any());
  }

  @Test
  @DisplayName("PUT presigned — 5MB 초과 시 INVALID_FILE_SIZE")
  void generateUploadUrl_oversize() {
    assertThatThrownBy(
            () ->
                s3Service.generateUploadUrl(
                    1L, new PresignedUrlRequest("photo.jpg", "image/jpeg", 6L * 1024 * 1024)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_FILE_SIZE);
  }

  @Test
  @DisplayName("GET signed URL 발급")
  void generateViewUrl_success() throws MalformedURLException {
    given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
        .willReturn(presignedGetObjectRequest);
    given(presignedGetObjectRequest.url())
        .willReturn(new URL("https://test-bucket.s3.ap-northeast-2.amazonaws.com/get"));

    ViewUrlResponse response = s3Service.generateViewUrl("quiz-images/some/key.jpg");

    assertThat(response.key()).isEqualTo("quiz-images/some/key.jpg");
    assertThat(response.expiresIn()).isEqualTo(3600L);
    assertThat(response.expiresAt()).isNotNull();
  }

  @Test
  @DisplayName("PATCH /complete — S3 HEAD 검증 후 COMPLETED 처리")
  void completeUpload_success() {
    User user = testUser();
    UploadMeta meta =
        UploadMeta.builder()
            .user(user)
            .s3Key("quiz-images/uid/abc.jpg")
            .originalName("photo.jpg")
            .contentType("image/jpeg")
            .sizeBytes(null)
            .build();
    given(uploadMetaRepository.findByS3Key("quiz-images/uid/abc.jpg"))
        .willReturn(Optional.of(meta));
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willReturn(
            HeadObjectResponse.builder().contentType("image/jpeg").contentLength(2048L).build());

    s3Service.completeUpload(1L, "quiz-images/uid/abc.jpg");

    assertThat(meta.getStatus()).isEqualTo(UploadStatus.COMPLETED);
    assertThat(meta.getSizeBytes()).isEqualTo(2048L);
  }

  @Test
  @DisplayName("PATCH /complete — 다른 사용자 메타 접근 시 UPLOAD_FORBIDDEN")
  void completeUpload_forbidden() {
    User other = testUser();
    ReflectionTestUtils.setField(other, "id", 999L);
    UploadMeta meta =
        UploadMeta.builder()
            .user(other)
            .s3Key("quiz-images/uid/abc.jpg")
            .contentType("image/jpeg")
            .build();
    given(uploadMetaRepository.findByS3Key("quiz-images/uid/abc.jpg"))
        .willReturn(Optional.of(meta));

    assertThatThrownBy(() -> s3Service.completeUpload(1L, "quiz-images/uid/abc.jpg"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.UPLOAD_FORBIDDEN);
  }

  @Test
  @DisplayName("verifyKeyOwnedAndCompleted — PENDING 상태면 UPLOAD_VERIFICATION_FAILED")
  void verifyKey_pendingFails() {
    User user = testUser();
    UploadMeta pending =
        UploadMeta.builder()
            .user(user)
            .s3Key("quiz-images/uid/pending.jpg")
            .contentType("image/jpeg")
            .build();
    given(uploadMetaRepository.findByS3Key("quiz-images/uid/pending.jpg"))
        .willReturn(Optional.of(pending));

    assertThatThrownBy(
            () -> s3Service.verifyKeyOwnedAndCompleted(1L, "quiz-images/uid/pending.jpg"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.UPLOAD_VERIFICATION_FAILED);
  }

  @Test
  @DisplayName("파일 삭제 호출")
  void delete_callsS3Client() {
    s3Service.delete("quiz-images/uid/abc.jpg");

    then(s3Client).should().deleteObject(any(DeleteObjectRequest.class));
  }
}
