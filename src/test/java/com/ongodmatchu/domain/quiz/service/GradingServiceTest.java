package com.ongodmatchu.domain.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.dto.GradeRequest;
import com.ongodmatchu.domain.quiz.dto.GradeResponse;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.ai.AiGradingService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("GradingService")
class GradingServiceTest {

  @InjectMocks private GradingService gradingService;
  @Mock private QuestionRepository questionRepository;
  @Mock private AiGradingService aiGradingService;

  private Question testQuestion(String answer) {
    User user =
        User.builder()
            .email("u@example.com")
            .nickname("작성자")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    Quiz quiz = Quiz.builder().user(user).title("퀴즈").category("게임").build();
    Question question = Question.builder().quiz(quiz).orderNum(1).answer(answer).build();
    ReflectionTestUtils.setField(question, "id", 1L);
    return question;
  }

  @Test
  @DisplayName("완전 일치 — AI 호출 없이 정답 처리")
  void grade_exactMatch_noAiCall() {
    given(questionRepository.findById(1L)).willReturn(Optional.of(testQuestion("태극기")));

    GradeResponse result = gradingService.grade(new GradeRequest(1L, "태극기"));

    assertThat(result.correct()).isTrue();
    then(aiGradingService).should(never()).grade(anyString(), anyString());
  }

  @Test
  @DisplayName("대소문자/공백 무시 완전 일치 — AI 호출 없이 정답 처리")
  void grade_exactMatchCaseInsensitive_noAiCall() {
    given(questionRepository.findById(1L)).willReturn(Optional.of(testQuestion("BTS")));

    GradeResponse result = gradingService.grade(new GradeRequest(1L, " bts "));

    assertThat(result.correct()).isTrue();
    then(aiGradingService).should(never()).grade(anyString(), anyString());
  }

  @Test
  @DisplayName("불일치 — AI 호출 후 정답 판정")
  void grade_aiCorrect() {
    given(questionRepository.findById(1L)).willReturn(Optional.of(testQuestion("대한민국")));
    given(aiGradingService.grade("대한민국", "한국")).willReturn(true);

    GradeResponse result = gradingService.grade(new GradeRequest(1L, "한국"));

    assertThat(result.correct()).isTrue();
  }

  @Test
  @DisplayName("불일치 — AI 호출 후 오답 판정")
  void grade_aiIncorrect() {
    given(questionRepository.findById(1L)).willReturn(Optional.of(testQuestion("대한민국")));
    given(aiGradingService.grade("대한민국", "일본")).willReturn(false);

    GradeResponse result = gradingService.grade(new GradeRequest(1L, "일본"));

    assertThat(result.correct()).isFalse();
    assertThat(result.correctAnswer()).isEqualTo("대한민국");
  }

  @Test
  @DisplayName("존재하지 않는 문제 — 예외 발생")
  void grade_questionNotFound() {
    given(questionRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> gradingService.grade(new GradeRequest(99L, "답변")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUESTION_NOT_FOUND);
  }

  @Test
  @DisplayName("단일 문자 대소문자 무시 — 정답 처리")
  void grade_singleChar_caseInsensitive() {
    given(questionRepository.findById(1L)).willReturn(Optional.of(testQuestion("A")));

    GradeResponse result = gradingService.grade(new GradeRequest(1L, "a"));

    assertThat(result.correct()).isTrue();
    then(aiGradingService).should(never()).grade(anyString(), anyString());
  }

  @Test
  @DisplayName("긴 문자열 완전 일치 — AI 호출 없이 정답 처리")
  void grade_longString_exactMatch() {
    String longAnswer = "서울은 대한민국의 수도이며 한반도의 중서부에 위치한 대도시입니다";
    given(questionRepository.findById(1L)).willReturn(Optional.of(testQuestion(longAnswer)));

    GradeResponse result = gradingService.grade(new GradeRequest(1L, "  " + longAnswer + "  "));

    assertThat(result.correct()).isTrue();
    assertThat(result.correctAnswer()).isEqualTo(longAnswer);
    then(aiGradingService).should(never()).grade(anyString(), anyString());
  }

  @Test
  @DisplayName("응답에 정답 포함 — GradeResponse 검증")
  void grade_responseContent() {
    given(questionRepository.findById(1L)).willReturn(Optional.of(testQuestion("정답")));
    given(aiGradingService.grade("정답", "다른답")).willReturn(false);

    GradeResponse result = gradingService.grade(new GradeRequest(1L, "다른답"));

    assertThat(result.questionId()).isEqualTo(1L);
    assertThat(result.correctAnswer()).isEqualTo("정답");
  }
}
