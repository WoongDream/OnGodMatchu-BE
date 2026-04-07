package com.ongodmatchu.domain.auth.oauth2;

import java.util.Map;

public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

  private final Map<String, Object> attributes;
  private final Map<String, Object> kakaoAccount;
  private final Map<String, Object> profile;

  @SuppressWarnings("unchecked")
  public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
    this.attributes = attributes;
    this.kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
    this.profile = (Map<String, Object>) kakaoAccount.get("profile");
  }

  @Override
  public String getId() {
    return String.valueOf(attributes.get("id"));
  }

  @Override
  public String getEmail() {
    return (String) kakaoAccount.get("email");
  }

  @Override
  public String getNickname() {
    return (String) profile.get("nickname");
  }
}
