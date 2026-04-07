package com.ongodmatchu.domain.auth.oauth2;

import java.util.Map;

public class NaverOAuth2UserInfo implements OAuth2UserInfo {

  private final Map<String, Object> attributes;

  @SuppressWarnings("unchecked")
  public NaverOAuth2UserInfo(Map<String, Object> attributes) {
    this.attributes = (Map<String, Object>) attributes.get("response");
  }

  @Override
  public String getId() {
    return (String) attributes.get("id");
  }

  @Override
  public String getEmail() {
    return (String) attributes.get("email");
  }

  @Override
  public String getNickname() {
    return (String) attributes.get("name");
  }
}
