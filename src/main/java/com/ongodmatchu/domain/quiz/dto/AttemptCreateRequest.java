package com.ongodmatchu.domain.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AttemptCreateRequest(
    @NotEmpty(message = "답변은 1개 이상 입력해주세요.") @Valid List<AttemptAnswerRequest> answers) {}
