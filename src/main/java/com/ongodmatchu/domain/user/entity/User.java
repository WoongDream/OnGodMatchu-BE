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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

  /** 프로필 이미지 크롭 전 원본 key. 재편집용. null 이면 원본 미보존(자동 생성 SVG 또는 레거시). */
  private String originalProfileImageKey;

  /** 프로필 이미지 크롭/변환 파라미터 (FE 소유 opaque JSON). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String profileImageTransform;

  @Column(length = 100)
  private String bio;

  @Column(nullable = false)
  private boolean isProfilePublic = true;

  private LocalDateTime lastActiveAt;

  @Column(nullable = false)
  private boolean isActive = true;

  /** 시스템(관리자) 계정 표시 — 탈퇴 사용자의 퀴즈 작성자 이전 대상. 인증/로그인 차단 대상이기도 함. */
  @Column(nullable = false)
  private boolean isSystem = false;

  private LocalDateTime deletedAt;

  private String termsVersion;

  private String privacyVersion;

  private LocalDateTime termsAgreedAt;

  @Column(nullable = false)
  private boolean agreedToAge14 = false;

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
    this.isActive = true;
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

  /** 크롭 결과 key + 원본 key + transform 을 함께 갱신 (재편집 업로드 시). */
  public void updateProfileImage(
      String profileImageKey, String originalProfileImageKey, String profileImageTransform) {
    this.profileImageKey = profileImageKey;
    this.originalProfileImageKey = originalProfileImageKey;
    this.profileImageTransform = profileImageTransform;
  }

  public void clearProfileImage() {
    this.profileImageKey = null;
    this.originalProfileImageKey = null;
    this.profileImageTransform = null;
  }

  public void updatePassword(String encodedPassword) {
    this.password = encodedPassword;
  }

  public void touchLastActiveAt() {
    this.lastActiveAt = LocalDateTime.now();
  }

  /** 가입 시점 약관 동의 기록. 재동의 플로우는 후속 작업. */
  public void agreeToTerms(String termsVersion, String privacyVersion, boolean agreedToAge14) {
    this.termsVersion = termsVersion;
    this.privacyVersion = privacyVersion;
    this.termsAgreedAt = LocalDateTime.now();
    this.agreedToAge14 = agreedToAge14;
  }

  public void withdraw(String anonymizedEmail, String anonymizedNickname) {
    this.email = anonymizedEmail;
    this.nickname = anonymizedNickname;
    this.password = null;
    this.bio = null;
    this.profileImageKey = null;
    this.originalProfileImageKey = null;
    this.profileImageTransform = null;
    this.isProfilePublic = false;
    this.isActive = false;
    this.deletedAt = LocalDateTime.now();
  }
}
