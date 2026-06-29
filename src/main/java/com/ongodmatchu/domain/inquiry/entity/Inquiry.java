package com.ongodmatchu.domain.inquiry.entity;

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
import org.springframework.data.annotation.LastModifiedDate;

/** 사용자 문의. 답변은 별도 저장하지 않고 {@code user_notifications.related_inquiry_id} 로 연결한다(1:N). */
@Entity
@Table(name = "inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 50)
  private String title;

  @Column(nullable = false, length = 1000)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InquiryStatus status = InquiryStatus.PENDING;

  @LastModifiedDate
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Builder
  private Inquiry(User user, String title, String content) {
    this.user = user;
    this.title = title;
    this.content = content;
    this.status = InquiryStatus.PENDING;
  }

  /** BO 수동 상태 전환. null·동일 상태는 무시. 답변 발송과 독립. */
  public void changeStatus(InquiryStatus status) {
    if (status == null || status == this.status) {
      return;
    }
    this.status = status;
  }
}
