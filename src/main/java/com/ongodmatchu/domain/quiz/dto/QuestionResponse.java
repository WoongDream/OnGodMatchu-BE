package com.ongodmatchu.domain.quiz.dto;

import com.ongodmatchu.domain.question.entity.Question;

public record QuestionResponse(
    Long id, int orderNum, String imageUrl, String questionText, String answer) {

  public static QuestionResponse from(Question question) {
    return new QuestionResponse(
        question.getId(),
        question.getOrderNum(),
        question.getImageUrl(),
        question.getQuestionText(),
        question.getAnswer());
  }
}
