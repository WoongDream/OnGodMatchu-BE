package com.ongodmatchu.domain.notification.entity;

import com.ongodmatchu.domain.inquiry.entity.Inquiry;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자가 특정 사용자에게 발송한 알림. 사용자는 로그인/폴링 시 미확인 알림을 modal 로 수신한다. */
@Entity
@Table(name = "user_notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotification extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "target_user_id", nullable = false)
  private User targetUser;

  /** 발송한 관리자. 표시는 운영팀으로 통일하므로 추적용으로만 보관. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sender_id")
  private User sender;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationType type;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  /** 사용자가 modal 을 확인한 시각. null 이면 미확인. */
  private LocalDateTime readAt;

  /** 문의 답변으로 발송된 알림이면 해당 문의에 연결(1문의:N알림). 문의 무관 알림은 null. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "related_inquiry_id")
  private Inquiry relatedInquiry;

  @Builder
  private UserNotification(
      User targetUser,
      User sender,
      NotificationType type,
      String title,
      String content,
      Inquiry relatedInquiry) {
    this.targetUser = targetUser;
    this.sender = sender;
    this.type = type;
    this.title = title;
    this.content = content;
    this.relatedInquiry = relatedInquiry;
  }

  public boolean isRead() {
    return readAt != null;
  }

  public void markRead() {
    if (readAt == null) {
      this.readAt = LocalDateTime.now();
    }
  }
}
