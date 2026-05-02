package com.ongodmatchu.domain.quiz.dto;

import com.ongodmatchu.domain.quiz.entity.QuizCategory;

public record CategoryResponse(String key, String label) {

  public static CategoryResponse from(QuizCategory category) {
    return new CategoryResponse(category.getKey(), category.getLabel());
  }
}
