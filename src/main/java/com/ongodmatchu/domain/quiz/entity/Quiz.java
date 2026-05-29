package com.ongodmatchu.domain.quiz.entity;

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
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Table(name = "quizzes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, updatable = false)
  private UUID publicId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private String title;

  private String description;

  @Column(nullable = false)
  private String category;

  private String thumbnailKey;

  /** 크롭 전 원본 이미지 key. 재편집 시 원본에서 다시 크롭하기 위해 보존. null 이면 원본 미보존(레거시). */
  private String originalThumbnailKey;

  /** 크롭/회전/반전 파라미터 (FE 소유 opaque JSON). BE 는 해석하지 않고 jsonb 로 저장/반환만. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String thumbnailTransform;

  @Column(nullable = false)
  private int playCount;

  @Column(nullable = false)
  private int starCount;

  @Column(nullable = false)
  private int commentCount;

  @Column(nullable = false)
  private int shareCount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private QuizVisibility visibility;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  private Quiz(
      User user,
      String title,
      String description,
      String category,
      String thumbnailKey,
      String originalThumbnailKey,
      String thumbnailTransform,
      QuizVisibility visibility) {
    this.user = user;
    this.title = title;
    this.description = description;
    this.category = category;
    this.thumbnailKey = thumbnailKey;
    this.originalThumbnailKey = originalThumbnailKey;
    this.thumbnailTransform = thumbnailTransform;
    this.playCount = 0;
    this.starCount = 0;
    this.commentCount = 0;
    this.shareCount = 0;
    this.visibility = visibility != null ? visibility : QuizVisibility.PRIVATE;
  }

  @PrePersist
  private void assignPublicId() {
    if (this.publicId == null) {
      this.publicId = UUID.randomUUID();
    }
  }

  public void incrementPlayCount() {
    this.playCount++;
  }

  public void updateTitle(String title) {
    this.title = title;
  }

  public void updateDescription(String description) {
    this.description = description;
  }

  public void updateCategory(String category) {
    this.category = category;
  }

  public void updateThumbnailKey(String thumbnailKey) {
    this.thumbnailKey = thumbnailKey;
  }

  /** 크롭 결과 key + 원본 key + transform 을 함께 갱신 (재편집 업로드 시). */
  public void updateThumbnail(
      String thumbnailKey, String originalThumbnailKey, String thumbnailTransform) {
    this.thumbnailKey = thumbnailKey;
    this.originalThumbnailKey = originalThumbnailKey;
    this.thumbnailTransform = thumbnailTransform;
  }

  public void changeVisibility(QuizVisibility visibility) {
    this.visibility = visibility;
  }

  public void incrementStarCount() {
    this.starCount++;
  }

  public void decrementStarCount() {
    if (this.starCount > 0) this.starCount--;
  }

  public void incrementCommentCount() {
    this.commentCount++;
  }

  public void decrementCommentCount() {
    if (this.commentCount > 0) this.commentCount--;
  }

  public void incrementShareCount() {
    this.shareCount++;
  }

  /**
   * questions 만 변경된 경우에도 updatedAt 이 갱신되도록 강제 마킹. quiz 자체 필드가 변경되지 않으면 @LastModifiedDate 가 트리거되지
   * 않음.
   */
  public void touch() {
    this.updatedAt = LocalDateTime.now();
  }
}
