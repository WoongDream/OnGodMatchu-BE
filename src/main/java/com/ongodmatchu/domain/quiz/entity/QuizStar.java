package com.ongodmatchu.domain.quiz.entity;

import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "quiz_stars",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "quiz_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizStar extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "quiz_id", nullable = false)
  private Quiz quiz;

  @Builder
  private QuizStar(User user, Quiz quiz) {
    this.user = user;
    this.quiz = quiz;
  }
}
