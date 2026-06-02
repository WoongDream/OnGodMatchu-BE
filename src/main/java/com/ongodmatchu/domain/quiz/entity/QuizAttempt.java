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

  /** 풀이 당시 문항당 타이머 설정(초). null = 타이머 없음(또는 레거시 기록). */
  @Column(name = "time_limit_sec")
  private Integer timeLimitSec;

  /** 풀이 당시 상위 백분위 스냅샷(0~100). null = 첫 응시(응시 < 2) 또는 레거시 기록. */
  @Column(name = "top_percentile")
  private Double topPercentile;

  @Builder
  private QuizAttempt(User user, Quiz quiz, int score, int totalQuestions, Integer timeLimitSec) {
    this.user = user;
    this.quiz = quiz;
    this.score = score;
    this.totalQuestions = totalQuestions;
    this.timeLimitSec = timeLimitSec;
  }

  /** 채점 후 산출한 상위 백분위 스냅샷을 기록 (관리되는 엔티티의 dirty checking 으로 flush). */
  public void assignTopPercentile(Double topPercentile) {
    this.topPercentile = topPercentile;
  }

  @PrePersist
  private void assignCompletedAt() {
    if (this.completedAt == null) {
      this.completedAt = LocalDateTime.now();
    }
  }
}
