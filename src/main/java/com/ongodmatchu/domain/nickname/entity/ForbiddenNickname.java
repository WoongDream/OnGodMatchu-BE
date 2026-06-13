package com.ongodmatchu.domain.nickname.entity;

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

/**
 * 사용 불가 닉네임 규칙. {@code rawValue} 는 운영자가 입력한 표시용 원본, {@code normalizedValue} 는 Tier 0·1 정규화된 매칭 키.
 * 차단 판정은 정규화 키끼리 {@link NicknameMatchType} 방식으로 비교.
 */
@Entity
@Table(name = "forbidden_nicknames")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ForbiddenNickname extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "raw_value", nullable = false, length = 100)
  private String rawValue;

  @Column(name = "normalized_value", nullable = false, length = 100)
  private String normalizedValue;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ForbiddenNicknameType type;

  @Enumerated(EnumType.STRING)
  @Column(name = "match_type", nullable = false, length = 20)
  private NicknameMatchType matchType;

  @Column(length = 200)
  private String reason;

  @Builder
  private ForbiddenNickname(
      String rawValue,
      String normalizedValue,
      ForbiddenNicknameType type,
      NicknameMatchType matchType,
      String reason) {
    this.rawValue = rawValue;
    this.normalizedValue = normalizedValue;
    this.type = type;
    this.matchType = matchType;
    this.reason = reason;
  }

  public void update(
      String rawValue,
      String normalizedValue,
      ForbiddenNicknameType type,
      NicknameMatchType matchType,
      String reason) {
    this.rawValue = rawValue;
    this.normalizedValue = normalizedValue;
    this.type = type;
    this.matchType = matchType;
    this.reason = reason;
  }
}
