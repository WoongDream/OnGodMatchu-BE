package com.ongodmatchu.domain.quiz.dto;

import com.ongodmatchu.domain.question.entity.Question;

public record QuestionResponse(
    Long id,
    int orderNum,
    String imageKey,
    String imageUrl,
    String answerImageKey,
    String answerImageUrl,
    String questionText,
    String answer) {

  public static QuestionResponse from(Question question, String imageUrl, String answerImageUrl) {
    return new QuestionResponse(
        question.getId(),
        question.getOrderNum(),
        question.getImageKey(),
        imageUrl,
        question.getAnswerImageKey(),
        answerImageUrl,
        question.getQuestionText(),
        question.getAnswer());
  }
}
