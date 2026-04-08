package com.ongodmatchu.infra.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.net.MalformedURLException;
import java.net.URL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

  @InjectMocks private S3Service s3Service;
  @Mock private S3Client s3Client;
  @Mock private S3Presigner s3Presigner;
  @Mock private PresignedPutObjectRequest presignedPutObjectRequest;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(s3Service, "bucket", "test-bucket");
    ReflectionTestUtils.setField(s3Service, "region", "ap-northeast-2");
  }

  @Test
  @DisplayName("Presigned URL 발급 성공")
  void generatePresignedUrl_success() throws MalformedURLException {
    given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .willReturn(presignedPutObjectRequest);
    given(presignedPutObjectRequest.url()).willReturn(new URL("https://s3.amazonaws.com/test"));

    PresignedUrlResponse response = s3Service.generatePresignedUrl("thumbnails", "image.png");

    assertThat(response.uploadUrl()).contains("s3.amazonaws.com");
    assertThat(response.fileUrl()).contains("test-bucket");
    assertThat(response.key()).startsWith("thumbnails/");
    assertThat(response.key()).endsWith(".png");
  }

  @Test
  @DisplayName("파일 삭제 호출")
  void delete_callsS3Client() {
    s3Service.delete("thumbnails/some-file.png");

    then(s3Client).should().deleteObject(any(DeleteObjectRequest.class));
  }
}
