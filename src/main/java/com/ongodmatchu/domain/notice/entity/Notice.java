package com.ongodmatchu.domain.notice.entity;

import com.ongodmatchu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NoticeStatus status = NoticeStatus.DRAFT;

  /** 게시 중인 공지를 공개 목록 최상단에 고정. 고정(라벨) = 게시 + pinned. */
  @Column(nullable = false)
  private boolean pinned = false;

  @Column(nullable = false)
  private long viewCount = 0;

  /** 최초 게시 시각. 공개 목록 정렬 기준. 임시저장만 거친 공지는 null. */
  private LocalDateTime publishedAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Builder
  private Notice(String title, String content, NoticeStatus status, boolean pinned) {
    this.title = title;
    this.content = content;
    this.status = status == null ? NoticeStatus.DRAFT : status;
    this.pinned = pinned;
    if (this.status == NoticeStatus.PUBLISHED) {
      this.publishedAt = LocalDateTime.now();
    }
  }

  public void updateContent(String title, String content) {
    if (title != null) {
      this.title = title;
    }
    if (content != null) {
      this.content = content;
    }
  }

  /** 상태 전환. 최초 게시 시 publishedAt 기록(이후 보존). */
  public void changeStatus(NoticeStatus status) {
    if (status == null || status == this.status) {
      return;
    }
    this.status = status;
    if (status == NoticeStatus.PUBLISHED && this.publishedAt == null) {
      this.publishedAt = LocalDateTime.now();
    }
  }

  public void changePinned(boolean pinned) {
    this.pinned = pinned;
  }

  public void increaseViewCount() {
    this.viewCount++;
  }

  public boolean isPublished() {
    return this.status == NoticeStatus.PUBLISHED;
  }
}
