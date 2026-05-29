package com.ongodmatchu.domain.quiz.entity;

import com.ongodmatchu.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "quiz_share")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizShare {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "quiz_id", nullable = false)
  private Quiz quiz;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "anon_id", length = 36)
  private String anonId;

  @Column(name = "shared_at", nullable = false, updatable = false)
  private LocalDateTime sharedAt;

  @Builder
  private QuizShare(Quiz quiz, User user, String anonId) {
    this.quiz = quiz;
    this.user = user;
    this.anonId = anonId;
  }

  @PrePersist
  private void onCreate() {
    if (this.sharedAt == null) {
      this.sharedAt = LocalDateTime.now();
    }
  }
}
