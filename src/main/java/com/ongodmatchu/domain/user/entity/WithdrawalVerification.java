package com.ongodmatchu.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원탈퇴 본인 인증 코드. 가입용 EmailVerification 과 별도 운영. */
@Entity
@Table(name = "withdrawal_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawalVerification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 6)
  private String code;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  private LocalDateTime consumedAt;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Builder
  private WithdrawalVerification(Long userId, String code, LocalDateTime expiresAt) {
    this.userId = userId;
    this.code = code;
    this.expiresAt = expiresAt;
    this.createdAt = LocalDateTime.now();
  }

  public boolean isExpired() {
    return LocalDateTime.now().isAfter(expiresAt);
  }

  public boolean isConsumed() {
    return consumedAt != null;
  }

  public void consume() {
    this.consumedAt = LocalDateTime.now();
  }
}
