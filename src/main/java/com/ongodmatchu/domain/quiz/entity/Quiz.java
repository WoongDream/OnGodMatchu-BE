package com.ongodmatchu.domain.quiz.entity;

import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "quizzes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private String title;

  private String description;

  @Column(nullable = false)
  private String category;

  private String thumbnailUrl;

  @Column(nullable = false)
  private int playCount;

  @Builder
  private Quiz(User user, String title, String description, String category, String thumbnailUrl) {
    this.user = user;
    this.title = title;
    this.description = description;
    this.category = category;
    this.thumbnailUrl = thumbnailUrl;
    this.playCount = 0;
  }

  public void incrementPlayCount() {
    this.playCount++;
  }
}
