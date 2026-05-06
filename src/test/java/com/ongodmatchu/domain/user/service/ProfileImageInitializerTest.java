package com.ongodmatchu.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.infra.s3.S3Service;
import com.ongodmatchu.infra.s3.UploadPolicy;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProfileImageInitializerTest {

  @InjectMocks private ProfileImageInitializer initializer;
  @Mock private ProfileImageGenerator generator;
  @Mock private S3Service s3Service;

  private User newUserWithoutImage(String nickname) {
    User user =
        User.builder()
            .email("user@example.com")
            .nickname(nickname)
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    UUID publicId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "publicId", publicId);
    return user;
  }

  private User newUserWithImage(String nickname, String existingKey) {
    User user = newUserWithoutImage(nickname);
    user.updateProfileImageKey(existingKey);
    return user;
  }

  // ============ initialize — 이미지 없는 경우 ============

  @Test
  @DisplayName("initialize_이미지키없음_SVG생성후S3업로드_키세팅")
  void initialize_noExistingKey_generatesSvgAndUploads() {
    User user = newUserWithoutImage("홍길동");
    byte[] svgBytes = "<svg/>".getBytes();
    given(generator.generateSvg("홍길동")).willReturn(svgBytes);

    initializer.initialize(user);

    then(generator).should().generateSvg("홍길동");
    then(s3Service)
        .should()
        .putObject(any(String.class), eq(svgBytes), eq(ProfileImageGenerator.CONTENT_TYPE));
    String key = user.getProfileImageKey();
    assertThat(key)
        .isNotNull()
        .startsWith(UploadPolicy.PROFILE_IMAGES_PREFIX + "/" + user.getPublicId() + "/")
        .endsWith(".svg");
  }

  @Test
  @DisplayName("initialize_이미지키없음_생성된키포맷검증")
  void initialize_noExistingKey_keyFormatIsCorrect() {
    User user = newUserWithoutImage("테스트유저");
    byte[] svgBytes = "<svg/>".getBytes();
    given(generator.generateSvg("테스트유저")).willReturn(svgBytes);

    initializer.initialize(user);

    String key = user.getProfileImageKey();
    assertThat(key)
        .startsWith(UploadPolicy.PROFILE_IMAGES_PREFIX + "/" + user.getPublicId() + "/")
        .endsWith(".svg");
    // {prefix}/{publicId}/{uuid}.svg — uuid 부분이 36자
    String[] parts = key.split("/");
    assertThat(parts).hasSize(3);
    assertThat(parts[2]).matches("[0-9a-f-]{36}\\.svg");
  }

  // ============ initialize — 이미지 있는 경우 (no-op) ============

  @Test
  @DisplayName("initialize_이미지키있음_S3호출없음_no_op")
  void initialize_existingKey_doesNothing() {
    User user = newUserWithImage("기존유저", "profile-images/existing/key.png");

    initializer.initialize(user);

    then(generator).should(never()).generateSvg(any());
    then(s3Service).should(never()).putObject(any(), any(), any());
    // 기존 키 유지
    assertThat(user.getProfileImageKey()).isEqualTo("profile-images/existing/key.png");
  }
}
