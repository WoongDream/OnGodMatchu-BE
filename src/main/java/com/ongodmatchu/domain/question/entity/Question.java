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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

  /** 문제 이미지 크롭 전 원본 key. 재편집용. null 이면 원본 미보존(레거시). */
  private String originalImageKey;

  /** 문제 이미지 크롭/변환 파라미터 (FE 소유 opaque JSON). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String imageTransform;

  private String answerImageKey;

  /** 정답 이미지 크롭 전 원본 key. */
  private String originalAnswerImageKey;

  /** 정답 이미지 크롭/변환 파라미터 (FE 소유 opaque JSON). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String answerImageTransform;

  private String questionText;

  @Column(nullable = false)
  private String answer;

  @Builder
  private Question(
      Quiz quiz,
      int orderNum,
      String imageKey,
      String originalImageKey,
      String imageTransform,
      String answerImageKey,
      String originalAnswerImageKey,
      String answerImageTransform,
      String questionText,
      String answer) {
    this.quiz = quiz;
    this.orderNum = orderNum;
    this.imageKey = imageKey;
    this.originalImageKey = originalImageKey;
    this.imageTransform = imageTransform;
    this.answerImageKey = answerImageKey;
    this.originalAnswerImageKey = originalAnswerImageKey;
    this.answerImageTransform = answerImageTransform;
    this.questionText = questionText;
    this.answer = answer;
  }

  public void update(
      int orderNum,
      String questionText,
      String answer,
      String imageKey,
      String originalImageKey,
      String imageTransform,
      String answerImageKey,
      String originalAnswerImageKey,
      String answerImageTransform) {
    this.orderNum = orderNum;
    this.questionText = questionText;
    this.answer = answer;
    this.imageKey = imageKey;
    this.originalImageKey = originalImageKey;
    this.imageTransform = imageTransform;
    this.answerImageKey = answerImageKey;
    this.originalAnswerImageKey = originalAnswerImageKey;
    this.answerImageTransform = answerImageTransform;
  }
}
