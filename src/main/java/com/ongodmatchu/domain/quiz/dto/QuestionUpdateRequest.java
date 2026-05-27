package com.ongodmatchu.domain.quiz.dto;

import jakarta.validation.constraints.NotBlank;

/** 퀴즈 편집 시 question 부분 갱신. id != null → 기존 갱신, id == null → 신규 추가. payload 에서 빠진 기존 id 는 삭제. */
public record QuestionUpdateRequest(
    Long id,
    String imageKey,
    String answerImageKey,
    String questionText,
    @NotBlank(message = "정답을 입력해주세요.") String answer) {}
