package com.ongodmatchu.domain.quiz.service;

import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.dto.GradeRequest;
import com.ongodmatchu.domain.quiz.dto.GradeResponse;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.ai.AiGradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GradingService {

  private final QuestionRepository questionRepository;
  private final AiGradingService aiGradingService;

  @Transactional(readOnly = true)
  public GradeResponse grade(GradeRequest request) {
    Question question =
        questionRepository
            .findById(request.questionId())
            .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));

    String correctAnswer = question.getAnswer();
    String userAnswer = request.userAnswer();

    boolean correct =
        isExactMatch(correctAnswer, userAnswer)
            || aiGradingService.grade(correctAnswer, userAnswer);

    return new GradeResponse(question.getId(), correct, correctAnswer);
  }

  /** 대소문자, 앞뒤 공백을 무시한 완전 일치 비교. 일치하면 AI 호출 없이 바로 정답 처리. */
  private boolean isExactMatch(String correctAnswer, String userAnswer) {
    return correctAnswer.trim().equalsIgnoreCase(userAnswer.trim());
  }
}
