package com.ongodmatchu.domain.user.service;

import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.infra.s3.S3Service;
import com.ongodmatchu.infra.s3.UploadPolicy;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileImageInitializer {

  private final ProfileImageGenerator generator;
  private final S3Service s3Service;

  /** 신규 가입자에게 랜덤 색 + 닉네임 첫 글자 SVG 이니셜 이미지를 생성해 S3 에 업로드하고 키를 세팅한다. 1회성. */
  public void initialize(User user) {
    if (user.getProfileImageKey() != null) {
      return;
    }
    String key = newKey(user);
    byte[] body = generator.generateSvg(user.getNickname());
    s3Service.putObject(key, body, ProfileImageGenerator.CONTENT_TYPE);
    user.updateProfileImageKey(key);
  }

  static String newKey(User user) {
    return UploadPolicy.PROFILE_IMAGES_PREFIX
        + "/"
        + user.getPublicId()
        + "/"
        + UUID.randomUUID()
        + ".svg";
  }
}
