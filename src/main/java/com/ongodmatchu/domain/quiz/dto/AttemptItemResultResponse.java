package com.ongodmatchu.domain.quiz.dto;

public record AttemptItemResultResponse(
    Long questionId, boolean correct, String correctAnswer, String userAnswer) {}
