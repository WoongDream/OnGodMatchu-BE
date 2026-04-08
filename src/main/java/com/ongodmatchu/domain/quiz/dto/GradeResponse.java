package com.ongodmatchu.domain.quiz.dto;

public record GradeResponse(Long questionId, boolean correct, String correctAnswer) {}
