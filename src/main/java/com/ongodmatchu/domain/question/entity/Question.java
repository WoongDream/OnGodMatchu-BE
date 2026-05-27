package com.ongodmatchu.domain.question.entity;

import com.ongodmatchu.domain.quiz.entity.Quiz;
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
@Table(name = "questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "quiz_id", nullable = false)
  private Quiz quiz;

  @Column(nullable = false)
  private int orderNum;

  private String imageKey;

  private String answerImageKey;

  private String questionText;

  @Column(nullable = false)
  private String answer;

  @Builder
  private Question(
      Quiz quiz,
      int orderNum,
      String imageKey,
      String answerImageKey,
      String questionText,
      String answer) {
    this.quiz = quiz;
    this.orderNum = orderNum;
    this.imageKey = imageKey;
    this.answerImageKey = answerImageKey;
    this.questionText = questionText;
    this.answer = answer;
  }

  public void update(
      int orderNum, String questionText, String answer, String imageKey, String answerImageKey) {
    this.orderNum = orderNum;
    this.questionText = questionText;
    this.answer = answer;
    this.imageKey = imageKey;
    this.answerImageKey = answerImageKey;
  }
}
