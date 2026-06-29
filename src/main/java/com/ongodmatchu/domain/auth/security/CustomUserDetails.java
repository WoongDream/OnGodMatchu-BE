package com.ongodmatchu.domain.auth.security;

import com.ongodmatchu.domain.user.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails, OAuth2User {

  @Getter private final User user;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
  }

  @Override
  public String getPassword() {
    return user.getPassword();
  }

  @Override
  public String getUsername() {
    return user.getEmail();
  }

  // OAuth2User
  @Override
  public Map<String, Object> getAttributes() {
    return Map.of();
  }

  @Override
  public String getName() {
    return user.getId().toString();
  }
}
