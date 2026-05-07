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

/** 퀴즈 풀이 1회 기록. 서버 채점(A안) 결과만 저장. 비로그인 풀이는 저장 X. */
@Entity
@Table(name = "quiz_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAttempt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "quiz_id", nullable = false)
  private Quiz quiz;

  @Column(nullable = false)
  private int score;

  @Column(nullable = false)
  private int totalQuestions;

  @Column(name = "completed_at", nullable = false, updatable = false)
  private LocalDateTime completedAt;

  @Builder
  private QuizAttempt(User user, Quiz quiz, int score, int totalQuestions) {
    this.user = user;
    this.quiz = quiz;
    this.score = score;
    this.totalQuestions = totalQuestions;
  }

  @PrePersist
  private void assignCompletedAt() {
    if (this.completedAt == null) {
      this.completedAt = LocalDateTime.now();
    }
  }
}
