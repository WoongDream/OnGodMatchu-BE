package com.ongodmatchu.domain.user.entity;

import com.ongodmatchu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String nickname;

  private String password;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AuthProvider provider;

  private String providerId;

  @Column(nullable = false)
  private boolean emailVerified;

  @Builder
  private User(
      String email,
      String nickname,
      String password,
      AuthProvider provider,
      String providerId,
      boolean emailVerified) {
    this.email = email;
    this.nickname = nickname;
    this.password = password;
    this.provider = provider;
    this.providerId = providerId;
    this.emailVerified = emailVerified;
  }

  public void verifyEmail() {
    this.emailVerified = true;
  }

  public void updateNickname(String nickname) {
    this.nickname = nickname;
  }
}
