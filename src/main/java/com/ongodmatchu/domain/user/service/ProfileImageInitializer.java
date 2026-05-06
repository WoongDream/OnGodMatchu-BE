package com.ongodmatchu.domain.user.service;

import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.infra.s3.S3Service;
import com.ongodmatchu.infra.s3.UploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileImageInitializer {

  private final ProfileImageGenerator generator;
  private final S3Service s3Service;

  /** 신규 가입자에게 닉네임 첫 글자 기반 SVG 이니셜 이미지를 만들어 S3 에 업로드하고 키를 세팅한다. */
  public void initialize(User user) {
    if (user.getProfileImageKey() != null) {
      return;
    }
    byte[] body = generator.generateSvg(user.getNickname());
    String key = UploadPolicy.PROFILE_IMAGES_PREFIX + "/" + user.getPublicId() + "/init.svg";
    s3Service.putObject(key, body, ProfileImageGenerator.CONTENT_TYPE);
    user.updateProfileImageKey(key);
  }
}
