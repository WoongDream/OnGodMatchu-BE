package com.ongodmatchu.domain.admin.entity;

import com.ongodmatchu.domain.notification.entity.UserNotification;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 백오피스 사용자 관리 이력. 저장 1회당 1행 — 수정 내용(changeType + detail) 과 관련 알림(relatedNotification) 이 페어를 이룬다.
 * 알림만 보낸 경우 changeType/detail 은 null, relatedNotification 만 채워진다.
 */
@Entity
@Table(name = "admin_user_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminUserHistory extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 수행 관리자. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "actor_id", nullable = false)
  private User actor;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "target_user_id", nullable = false)
  private User targetUser;

  @Enumerated(EnumType.STRING)
  @Column(name = "change_type", length = 40)
  private AdminUserChangeType changeType;

  @Column(columnDefinition = "TEXT")
  private String detail;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "related_notification_id")
  private UserNotification relatedNotification;

  @Builder
  private AdminUserHistory(
      User actor,
      User targetUser,
      AdminUserChangeType changeType,
      String detail,
      UserNotification relatedNotification) {
    this.actor = actor;
    this.targetUser = targetUser;
    this.changeType = changeType;
    this.detail = detail;
    this.relatedNotification = relatedNotification;
  }
}
