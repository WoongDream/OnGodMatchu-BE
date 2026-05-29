package com.ongodmatchu.domain.quiz.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 풀이 1회의 한 답안. userAnswer 는 빈 문자열 허용 (시간 초과로 입력 없이 자동 제출되는 케이스). 빈 답은 BE 채점에서 무조건 오답 처리. null 만 거부.
 */
public record AttemptAnswerRequest(
    @NotNull(message = "문제 ID를 입력해주세요.") Long questionId,
    @NotNull(message = "답변 필드는 필수예요.") String userAnswer) {}
