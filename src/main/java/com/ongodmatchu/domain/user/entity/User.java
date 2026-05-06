package com.ongodmatchu.domain.user.entity;

import com.ongodmatchu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
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

  @Column(nullable = false, unique = true, updatable = false)
  private UUID publicId;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false, unique = true)
  private String nickname;

  private String password;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AuthProvider provider;

  private String providerId;

  @Column(nullable = false)
  private boolean emailVerified;

  private String profileImageKey;

  @Column(length = 100)
  private String bio;

  @Column(nullable = false)
  private boolean isProfilePublic = true;

  private LocalDateTime lastActiveAt;

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
    this.isProfilePublic = true;
  }

  @PrePersist
  private void assignPublicId() {
    if (this.publicId == null) {
      this.publicId = UUID.randomUUID();
    }
  }

  public void verifyEmail() {
    this.emailVerified = true;
  }

  public void updateNickname(String nickname) {
    this.nickname = nickname;
  }

  public void updateBio(String bio) {
    this.bio = bio;
  }

  public void updateProfilePublic(boolean isProfilePublic) {
    this.isProfilePublic = isProfilePublic;
  }

  public void updateProfileImageKey(String profileImageKey) {
    this.profileImageKey = profileImageKey;
  }

  public void clearProfileImage() {
    this.profileImageKey = null;
  }

  public void updatePassword(String encodedPassword) {
    this.password = encodedPassword;
  }

  public void touchLastActiveAt() {
    this.lastActiveAt = LocalDateTime.now();
  }
}
