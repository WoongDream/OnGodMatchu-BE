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

/**
 * 퀴즈 풀이 1회 기록. 서버 채점(A안) 결과 저장. 비로그인 풀이는 user=null 로 저장 — 퀴즈 작성자 기준 집계(weeklyPlayCount /
 * correctRate)에 반영되도록 함. "내 풀이 기록" 조회는 user_id 로 필터하므로 비로그인 attempt 는 자연 제외.
 */
@Entity
@Table(name = "quiz_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAttempt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
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
