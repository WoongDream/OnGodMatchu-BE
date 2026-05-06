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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

  @Column(nullable = false)
  private int playCount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private QuizVisibility visibility;

  @Builder
  private Quiz(
      User user,
      String title,
      String description,
      String category,
      String thumbnailKey,
      QuizVisibility visibility) {
    this.user = user;
    this.title = title;
    this.description = description;
    this.category = category;
    this.thumbnailKey = thumbnailKey;
    this.playCount = 0;
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

  public void changeVisibility(QuizVisibility visibility) {
    this.visibility = visibility;
  }
}
