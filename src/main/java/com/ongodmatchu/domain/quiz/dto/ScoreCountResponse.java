package com.ongodmatchu.domain.quiz.dto;

/** 점수 분포의 한 칸. score 는 0~totalQuestions, count 는 해당 점수를 받은 응시 횟수. */
public record ScoreCountResponse(int score, long count) {}
