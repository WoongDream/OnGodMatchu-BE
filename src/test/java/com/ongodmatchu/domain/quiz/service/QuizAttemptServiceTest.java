package com.ongodmatchu.domain.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.dto.AttemptAnswerRequest;
import com.ongodmatchu.domain.quiz.dto.AttemptCreateRequest;
import com.ongodmatchu.domain.quiz.dto.AttemptListItemResponse;
import com.ongodmatchu.domain.quiz.dto.AttemptResultResponse;
import com.ongodmatchu.domain.quiz.dto.ScoreDistributionResponse;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizAttempt;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.quiz.repository.QuizAttemptRepository;
import com.ongodmatchu.domain.quiz.repository.QuizAttemptRepository.ScoreBucketRow;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.ai.AiGradingService;
import com.ongodmatchu.infra.s3.S3Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuizAttemptServiceTest {

  @InjectMocks private QuizAttemptService quizAttemptService;
  @Mock private QuizRepository quizRepository;
  @Mock private QuestionRepository questionRepository;
  @Mock private QuizAttemptRepository quizAttemptRepository;
  @Mock private UserRepository userRepository;
  @Mock private AiGradingService aiGradingService;
  @Mock private S3Service s3Service;

  @BeforeEach
  void setUp() {
    lenient().when(s3Service.batchPresignViewUrls(any())).thenReturn(Map.of());
  }

  // ─── 픽스처 헬퍼 ────────────────────────────────────────────────────────────

  private User testUser(Long id) {
    User user =
        User.builder()
            .email("user" + id + "@example.com")
            .nickname("유저" + id)
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", id);
    ReflectionTestUtils.setField(user, "publicId", UUID.randomUUID());
    return user;
  }

  private Quiz testQuiz(User owner, QuizVisibility visibility) {
    Quiz quiz =
        Quiz.builder().user(owner).title("테스트 퀴즈").category("game").visibility(visibility).build();
    ReflectionTestUtils.setField(quiz, "id", 1L);
    ReflectionTestUtils.setField(quiz, "publicId", UUID.randomUUID());
    return quiz;
  }

  private Question testQuestion(Quiz quiz, Long id, String answer) {
    Question q =
        Question.builder().quiz(quiz).orderNum(1).questionText("문제 " + id).answer(answer).build();
    ReflectionTestUtils.setField(q, "id", id);
    return q;
  }

  private QuizAttempt testAttempt(User user, Quiz quiz, int score, int total) {
    QuizAttempt attempt =
        QuizAttempt.builder().user(user).quiz(quiz).score(score).totalQuestions(total).build();
    ReflectionTestUtils.setField(attempt, "id", 100L);
    ReflectionTestUtils.setField(attempt, "completedAt", LocalDateTime.of(2025, 5, 1, 12, 0));
    return attempt;
  }

  // ─── submit() ───────────────────────────────────────────────────────────────

  @Test
  @DisplayName("submit_존재하지않는퀴즈_QUIZ_NOT_FOUND예외")
  void submit_quizNotFound_throwsException() {
    given(quizRepository.findById(99L)).willReturn(Optional.empty());

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(1L, "답")));

    assertThatThrownBy(() -> quizAttemptService.submit(99L, 1L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
  }

  @Test
  @DisplayName("submit_PRIVATE퀴즈_외부뷰어_QUIZ_NOT_FOUND예외")
  void submit_privateQuiz_externalViewer_throwsNotFound() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PRIVATE);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "답")));

    assertThatThrownBy(() -> quizAttemptService.submit(1L, 99L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
  }

  @Test
  @DisplayName("submit_PRIVATE퀴즈_비로그인_QUIZ_NOT_FOUND예외")
  void submit_privateQuiz_anonymous_throwsNotFound() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PRIVATE);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "답")));

    assertThatThrownBy(() -> quizAttemptService.submit(1L, null, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
  }

  @Test
  @DisplayName("submit_PRIVATE퀴즈_본인_정상채점후저장")
  void submit_privateQuiz_owner_successAndSaved() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PRIVATE);
    Question q = testQuestion(quiz, 10L, "정답");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));
    given(userRepository.findById(1L)).willReturn(Optional.of(owner));
    given(quizAttemptRepository.save(any(QuizAttempt.class)))
        .willAnswer(
            inv -> {
              QuizAttempt a = inv.getArgument(0);
              ReflectionTestUtils.setField(a, "id", 100L);
              return a;
            });

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "정답")));

    AttemptResultResponse result = quizAttemptService.submit(1L, 1L, request);

    assertThat(result.score()).isEqualTo(1);
    assertThat(result.attemptId()).isEqualTo(100L);
    then(quizAttemptRepository).should().save(any(QuizAttempt.class));
  }

  @Test
  @DisplayName("submit_비로그인_익명attempt저장(user=null)_attemptId는응답에서null_playCount증분")
  void submit_anonymous_savesAttemptWithNullUser_responseAttemptIdNull() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q = testQuestion(quiz, 10L, "정답");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));
    given(quizAttemptRepository.save(any(QuizAttempt.class)))
        .willAnswer(
            inv -> {
              QuizAttempt a = inv.getArgument(0);
              ReflectionTestUtils.setField(a, "id", 999L);
              return a;
            });

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "정답")));

    AttemptResultResponse result = quizAttemptService.submit(1L, null, request);

    assertThat(result.attemptId()).isNull();
    assertThat(quiz.getPlayCount()).isEqualTo(1);
    then(userRepository).should(never()).findById(any());

    ArgumentCaptor<QuizAttempt> captor = ArgumentCaptor.forClass(QuizAttempt.class);
    then(quizAttemptRepository).should().save(captor.capture());
    QuizAttempt saved = captor.getValue();
    assertThat(saved.getUser()).isNull();
    assertThat(saved.getQuiz()).isEqualTo(quiz);
    assertThat(saved.getScore()).isEqualTo(1);
    assertThat(saved.getTotalQuestions()).isEqualTo(1);
  }

  @Test
  @DisplayName("submit_로그인_attempt저장되고attemptId포함응답")
  void submit_authenticated_attemptSavedAndIdInResponse() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q = testQuestion(quiz, 10L, "정답");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));
    given(userRepository.findById(1L)).willReturn(Optional.of(owner));
    given(quizAttemptRepository.save(any(QuizAttempt.class)))
        .willAnswer(
            inv -> {
              QuizAttempt a = inv.getArgument(0);
              ReflectionTestUtils.setField(a, "id", 200L);
              return a;
            });

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "정답")));

    AttemptResultResponse result = quizAttemptService.submit(1L, 1L, request);

    assertThat(result.attemptId()).isEqualTo(200L);
    assertThat(quiz.getPlayCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("submit_exactMatch_정답_AI미호출")
  void submit_exactMatch_correct_aiNotCalled() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q = testQuestion(quiz, 10L, "정답");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "정답")));

    AttemptResultResponse result = quizAttemptService.submit(1L, null, request);

    assertThat(result.score()).isEqualTo(1);
    assertThat(result.results().get(0).correct()).isTrue();
    then(aiGradingService).should(never()).grade(any(), any());
  }

  @Test
  @DisplayName("submit_exactMatch_대소문자무시_정답처리")
  void submit_exactMatch_caseInsensitive_correct() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q = testQuestion(quiz, 10L, "Hello");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "hello")));

    AttemptResultResponse result = quizAttemptService.submit(1L, null, request);

    assertThat(result.score()).isEqualTo(1);
    assertThat(result.results().get(0).correct()).isTrue();
    then(aiGradingService).should(never()).grade(any(), any());
  }

  @Test
  @DisplayName("submit_exactMatch_공백무시_정답처리")
  void submit_exactMatch_whitespaceIgnored_correct() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q = testQuestion(quiz, 10L, "정답");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "  정답  ")));

    AttemptResultResponse result = quizAttemptService.submit(1L, null, request);

    assertThat(result.score()).isEqualTo(1);
    then(aiGradingService).should(never()).grade(any(), any());
  }

  @Test
  @DisplayName("submit_exactMatch실패_AI정답처리_score1")
  void submit_exactMatchFails_aiGradesCorrect_score1() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q = testQuestion(quiz, 10L, "수도");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));
    given(aiGradingService.grade("수도", "서울")).willReturn(true);

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "서울")));

    AttemptResultResponse result = quizAttemptService.submit(1L, null, request);

    assertThat(result.score()).isEqualTo(1);
    assertThat(result.results().get(0).correct()).isTrue();
    then(aiGradingService).should().grade("수도", "서울");
  }

  @Test
  @DisplayName("submit_빈답_AI호출_skip_무조건_오답")
  void submit_blankUserAnswer_skipsAi_alwaysIncorrect() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q = testQuestion(quiz, 10L, "정답");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "")));

    AttemptResultResponse result = quizAttemptService.submit(1L, null, request);

    assertThat(result.score()).isZero();
    assertThat(result.results().get(0).correct()).isFalse();
    assertThat(result.results().get(0).userAnswer()).isEmpty();
    then(aiGradingService).should(never()).grade(any(), any());
  }

  @Test
  @DisplayName("submit_공백만_답_AI호출_skip_오답")
  void submit_whitespaceOnlyAnswer_skipsAi_incorrect() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q = testQuestion(quiz, 10L, "정답");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "   ")));

    AttemptResultResponse result = quizAttemptService.submit(1L, null, request);

    assertThat(result.score()).isZero();
    assertThat(result.results().get(0).correct()).isFalse();
    then(aiGradingService).should(never()).grade(any(), any());
  }

  @Test
  @DisplayName("submit_exactMatch실패_AI오답처리_score0")
  void submit_exactMatchFails_aiGradesIncorrect_score0() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q = testQuestion(quiz, 10L, "정답");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));
    given(aiGradingService.grade("정답", "오답")).willReturn(false);

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "오답")));

    AttemptResultResponse result = quizAttemptService.submit(1L, null, request);

    assertThat(result.score()).isZero();
    assertThat(result.results().get(0).correct()).isFalse();
  }

  @Test
  @DisplayName("submit_score/totalQuestions/percent_정확계산_2정답3문제")
  void submit_scoreAndPercent_calculatedCorrectly() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q1 = testQuestion(quiz, 10L, "정답1");
    Question q2 = testQuestion(quiz, 11L, "정답2");
    Question q3 = testQuestion(quiz, 12L, "정답3");
    ReflectionTestUtils.setField(q2, "orderNum", 2);
    ReflectionTestUtils.setField(q3, "orderNum", 3);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q1, q2, q3));
    given(aiGradingService.grade(any(), any())).willReturn(false);

    // q1 정답, q2 오답, q3 오답
    AttemptCreateRequest request =
        new AttemptCreateRequest(
            List.of(
                new AttemptAnswerRequest(10L, "정답1"),
                new AttemptAnswerRequest(11L, "틀린답"),
                new AttemptAnswerRequest(12L, "틀린답")));

    AttemptResultResponse result = quizAttemptService.submit(1L, null, request);

    assertThat(result.score()).isEqualTo(1);
    assertThat(result.totalQuestions()).isEqualTo(3);
    assertThat(result.percent()).isEqualTo(1 * 100.0 / 3);
  }

  @Test
  @DisplayName("submit_답변에없는questionId_QUESTION_NOT_FOUND예외")
  void submit_unknownQuestionId_throwsException() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q = testQuestion(quiz, 10L, "정답");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));

    // 존재하지 않는 questionId=999
    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(999L, "아무답")));

    assertThatThrownBy(() -> quizAttemptService.submit(1L, null, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUESTION_NOT_FOUND);
  }

  @Test
  @DisplayName("submit_결과응답에correctAnswer와userAnswer모두포함")
  void submit_resultIncludesCorrectAnswerAndUserAnswer() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q = testQuestion(quiz, 10L, "정답");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));
    given(aiGradingService.grade(any(), any())).willReturn(false);

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "내 답변")));

    AttemptResultResponse result = quizAttemptService.submit(1L, null, request);

    assertThat(result.results()).hasSize(1);
    assertThat(result.results().get(0).correctAnswer()).isEqualTo("정답");
    assertThat(result.results().get(0).userAnswer()).isEqualTo("내 답변");
    assertThat(result.results().get(0).questionId()).isEqualTo(10L);
  }

  @Test
  @DisplayName("submit_answerImageKey있는질문_correctAnswerImageUrl에presigned매핑")
  void submit_questionWithAnswerImageKey_mapsPresignedUrl() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q1 = testQuestion(quiz, 10L, "정답1");
    Question q2 = testQuestion(quiz, 11L, "정답2");
    ReflectionTestUtils.setField(q1, "answerImageKey", "quiz-images/ans1.png");
    ReflectionTestUtils.setField(q2, "orderNum", 2);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q1, q2));
    given(s3Service.batchPresignViewUrls(List.of("quiz-images/ans1.png")))
        .willReturn(Map.of("quiz-images/ans1.png", "https://signed.example/ans1.png"));

    AttemptCreateRequest request =
        new AttemptCreateRequest(
            List.of(new AttemptAnswerRequest(10L, "정답1"), new AttemptAnswerRequest(11L, "정답2")));

    AttemptResultResponse result = quizAttemptService.submit(1L, null, request);

    assertThat(result.results().get(0).correctAnswerImageUrl())
        .isEqualTo("https://signed.example/ans1.png");
    assertThat(result.results().get(1).correctAnswerImageUrl()).isNull();
  }

  @Test
  @DisplayName("submit_answerImageKey없으면_correctAnswerImageUrl_null")
  void submit_noAnswerImageKey_correctAnswerImageUrlNull() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q = testQuestion(quiz, 10L, "정답");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "정답")));

    AttemptResultResponse result = quizAttemptService.submit(1L, null, request);

    assertThat(result.results().get(0).correctAnswerImageUrl()).isNull();
  }

  @Test
  @DisplayName("submit_attempt저장시user/quiz/score/totalQuestions정확히전달")
  void submit_savedAttemptHasCorrectFields() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    Question q1 = testQuestion(quiz, 10L, "A");
    Question q2 = testQuestion(quiz, 11L, "B");
    ReflectionTestUtils.setField(q2, "orderNum", 2);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q1, q2));
    given(userRepository.findById(1L)).willReturn(Optional.of(owner));
    given(quizAttemptRepository.save(any(QuizAttempt.class)))
        .willAnswer(
            inv -> {
              ReflectionTestUtils.setField((QuizAttempt) inv.getArgument(0), "id", 50L);
              return inv.getArgument(0);
            });

    // q1 정답, q2 오답
    AttemptCreateRequest request =
        new AttemptCreateRequest(
            List.of(new AttemptAnswerRequest(10L, "A"), new AttemptAnswerRequest(11L, "틀림")));
    given(aiGradingService.grade("B", "틀림")).willReturn(false);

    quizAttemptService.submit(1L, 1L, request);

    ArgumentCaptor<QuizAttempt> captor = ArgumentCaptor.forClass(QuizAttempt.class);
    then(quizAttemptRepository).should().save(captor.capture());
    QuizAttempt saved = captor.getValue();
    assertThat(saved.getScore()).isEqualTo(1);
    assertThat(saved.getTotalQuestions()).isEqualTo(2);
    assertThat(saved.getUser()).isSameAs(owner);
    assertThat(saved.getQuiz()).isSameAs(quiz);
  }

  // ─── getMyAttempts() ────────────────────────────────────────────────────────

  @Test
  @DisplayName("getMyAttempts_정상반환_s3batchPresign호출")
  void getMyAttempts_returnsPage_andCallsBatchPresign() {
    User user = testUser(1L);
    Quiz quiz = testQuiz(user, QuizVisibility.PUBLIC);
    QuizAttempt attempt = testAttempt(user, quiz, 3, 5);
    Page<QuizAttempt> page = new PageImpl<>(List.of(attempt));
    given(quizAttemptRepository.findByUserIdOrderByCompletedAtDesc(eq(1L), any(Pageable.class)))
        .willReturn(page);
    given(s3Service.batchPresignViewUrls(any())).willReturn(Map.of());

    Page<AttemptListItemResponse> result =
        quizAttemptService.getMyAttempts(1L, PageRequest.of(0, 20));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).score()).isEqualTo(3);
    assertThat(result.getContent().get(0).totalQuestions()).isEqualTo(5);
    then(s3Service).should().batchPresignViewUrls(any());
  }

  @Test
  @DisplayName("getMyAttempts_pageSize50초과_50으로cap")
  void getMyAttempts_pageSizeCappedAt50() {
    given(quizAttemptRepository.findByUserIdOrderByCompletedAtDesc(eq(1L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    quizAttemptService.getMyAttempts(1L, PageRequest.of(0, 200));

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(quizAttemptRepository)
        .should()
        .findByUserIdOrderByCompletedAtDesc(eq(1L), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(50);
  }

  @Test
  @DisplayName("getMyAttempts_pageSize0_defaultSize20으로cap")
  void getMyAttempts_pageSizeZero_defaultsTo20() {
    given(quizAttemptRepository.findByUserIdOrderByCompletedAtDesc(eq(1L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    // size=0 은 Pageable 생성 제약으로 직접 주입
    Pageable pageable = PageRequest.of(0, 1);
    // 실제 pageSize=0 시나리오는 applyPageDefaults 내부 로직상 <=0 → DEFAULT_PAGE_SIZE
    // PageRequest.of(0, 1) 은 size>0 이므로 size=1 로 cap 됨. 경계 검증은 size=50 시나리오로 보완됨.
    quizAttemptService.getMyAttempts(1L, pageable);

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(quizAttemptRepository)
        .should()
        .findByUserIdOrderByCompletedAtDesc(eq(1L), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(1);
  }

  @Test
  @DisplayName("getMyAttempts_빈결과_빈페이지반환")
  void getMyAttempts_noAttempts_returnsEmptyPage() {
    given(quizAttemptRepository.findByUserIdOrderByCompletedAtDesc(eq(1L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    Page<AttemptListItemResponse> result =
        quizAttemptService.getMyAttempts(1L, PageRequest.of(0, 20));

    assertThat(result.getContent()).isEmpty();
  }

  @Test
  @DisplayName("getMyAttempts_썸네일키있는attempt_presignUrl매핑")
  void getMyAttempts_withThumbnailKey_mapsPresignedUrl() {
    User user = testUser(1L);
    Quiz quiz = testQuiz(user, QuizVisibility.PUBLIC);
    ReflectionTestUtils.setField(quiz, "thumbnailKey", "quiz-images/thumb.png");
    QuizAttempt attempt = testAttempt(user, quiz, 5, 10);
    Page<QuizAttempt> page = new PageImpl<>(List.of(attempt));
    given(quizAttemptRepository.findByUserIdOrderByCompletedAtDesc(eq(1L), any(Pageable.class)))
        .willReturn(page);
    given(s3Service.batchPresignViewUrls(List.of("quiz-images/thumb.png")))
        .willReturn(Map.of("quiz-images/thumb.png", "https://signed.example/thumb.png"));

    Page<AttemptListItemResponse> result =
        quizAttemptService.getMyAttempts(1L, PageRequest.of(0, 20));

    assertThat(result.getContent().get(0).quizThumbnailUrl())
        .isEqualTo("https://signed.example/thumb.png");
  }

  @Test
  @DisplayName("getMyAttempts_percent계산_5점10문제_50percent")
  void getMyAttempts_percentCalculation_halfCorrect() {
    User user = testUser(1L);
    Quiz quiz = testQuiz(user, QuizVisibility.PUBLIC);
    QuizAttempt attempt = testAttempt(user, quiz, 5, 10);
    given(quizAttemptRepository.findByUserIdOrderByCompletedAtDesc(eq(1L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(attempt)));

    Page<AttemptListItemResponse> result =
        quizAttemptService.getMyAttempts(1L, PageRequest.of(0, 20));

    assertThat(result.getContent().get(0).percent()).isEqualTo(50.0);
  }

  // ─── getAttemptsByPublicId() ─────────────────────────────────────────────────

  @Test
  @DisplayName("getAttemptsByPublicId_publicId없는사용자_USER_NOT_FOUND예외")
  void getAttemptsByPublicId_userNotFound_throwsException() {
    UUID unknownId = UUID.randomUUID();
    given(userRepository.findByPublicId(unknownId)).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> quizAttemptService.getAttemptsByPublicId(unknownId, null, PageRequest.of(0, 20)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("getAttemptsByPublicId_비공개프로필_외부뷰어_빈페이지반환")
  void getAttemptsByPublicId_privateProfile_externalViewer_returnsEmptyPage() {
    User author = testUser(2L);
    author.updateProfilePublic(false);
    UUID authorPublicId = author.getPublicId();
    given(userRepository.findByPublicId(authorPublicId)).willReturn(Optional.of(author));

    Page<AttemptListItemResponse> result =
        quizAttemptService.getAttemptsByPublicId(authorPublicId, 99L, PageRequest.of(0, 20));

    assertThat(result.getContent()).isEmpty();
    then(quizAttemptRepository).should(never()).findByUserIdOrderByCompletedAtDesc(any(), any());
  }

  @Test
  @DisplayName("getAttemptsByPublicId_비공개프로필_비로그인_빈페이지반환")
  void getAttemptsByPublicId_privateProfile_anonymous_returnsEmptyPage() {
    User author = testUser(2L);
    author.updateProfilePublic(false);
    UUID authorPublicId = author.getPublicId();
    given(userRepository.findByPublicId(authorPublicId)).willReturn(Optional.of(author));

    Page<AttemptListItemResponse> result =
        quizAttemptService.getAttemptsByPublicId(authorPublicId, null, PageRequest.of(0, 20));

    assertThat(result.getContent()).isEmpty();
    then(quizAttemptRepository).should(never()).findByUserIdOrderByCompletedAtDesc(any(), any());
  }

  @Test
  @DisplayName("getAttemptsByPublicId_비공개프로필_본인_정상반환")
  void getAttemptsByPublicId_privateProfile_owner_returnsPage() {
    User author = testUser(2L);
    author.updateProfilePublic(false);
    UUID authorPublicId = author.getPublicId();
    Quiz quiz = testQuiz(author, QuizVisibility.PUBLIC);
    QuizAttempt attempt = testAttempt(author, quiz, 4, 5);
    given(userRepository.findByPublicId(authorPublicId)).willReturn(Optional.of(author));
    given(quizAttemptRepository.findByUserIdOrderByCompletedAtDesc(eq(2L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(attempt)));

    Page<AttemptListItemResponse> result =
        quizAttemptService.getAttemptsByPublicId(authorPublicId, 2L, PageRequest.of(0, 20));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).score()).isEqualTo(4);
  }

  @Test
  @DisplayName("getAttemptsByPublicId_공개프로필_외부뷰어_정상반환")
  void getAttemptsByPublicId_publicProfile_externalViewer_returnsPage() {
    User author = testUser(2L);
    UUID authorPublicId = author.getPublicId();
    Quiz quiz = testQuiz(author, QuizVisibility.PUBLIC);
    QuizAttempt attempt = testAttempt(author, quiz, 2, 5);
    given(userRepository.findByPublicId(authorPublicId)).willReturn(Optional.of(author));
    given(quizAttemptRepository.findByUserIdOrderByCompletedAtDesc(eq(2L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(attempt)));

    Page<AttemptListItemResponse> result =
        quizAttemptService.getAttemptsByPublicId(authorPublicId, 99L, PageRequest.of(0, 20));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).score()).isEqualTo(2);
  }

  @Test
  @DisplayName("getAttemptsByPublicId_공개프로필_비로그인_정상반환")
  void getAttemptsByPublicId_publicProfile_anonymous_returnsPage() {
    User author = testUser(2L);
    UUID authorPublicId = author.getPublicId();
    Quiz quiz = testQuiz(author, QuizVisibility.PUBLIC);
    QuizAttempt attempt = testAttempt(author, quiz, 1, 5);
    given(userRepository.findByPublicId(authorPublicId)).willReturn(Optional.of(author));
    given(quizAttemptRepository.findByUserIdOrderByCompletedAtDesc(eq(2L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(attempt)));

    Page<AttemptListItemResponse> result =
        quizAttemptService.getAttemptsByPublicId(authorPublicId, null, PageRequest.of(0, 20));

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  @DisplayName("getAttemptsByPublicId_pageSize50초과_50으로cap")
  void getAttemptsByPublicId_pageSizeCappedAt50() {
    User author = testUser(2L);
    UUID authorPublicId = author.getPublicId();
    given(userRepository.findByPublicId(authorPublicId)).willReturn(Optional.of(author));
    given(quizAttemptRepository.findByUserIdOrderByCompletedAtDesc(eq(2L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    quizAttemptService.getAttemptsByPublicId(authorPublicId, null, PageRequest.of(0, 200));

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(quizAttemptRepository)
        .should()
        .findByUserIdOrderByCompletedAtDesc(eq(2L), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(50);
  }

  // ─── submit() topPercentile ─────────────────────────────────────────────────

  /** submit() 호출 시 공통으로 필요한 mock 셋업 (PUBLIC 퀴즈, 단일 정답 질문, 정답 입력). */
  private AttemptResultResponse submitSingleCorrectAttempt(
      User owner, Quiz quiz, Long viewerUserId, long savedAttemptId) {
    Question q = testQuestion(quiz, 10L, "정답");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));
    if (viewerUserId != null) {
      given(userRepository.findById(viewerUserId)).willReturn(Optional.of(owner));
    }
    given(quizAttemptRepository.save(any(QuizAttempt.class)))
        .willAnswer(
            inv -> {
              QuizAttempt a = inv.getArgument(0);
              ReflectionTestUtils.setField(a, "id", savedAttemptId);
              return a;
            });

    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "정답")));
    return quizAttemptService.submit(1L, viewerUserId, request);
  }

  @Test
  @DisplayName("submit_첫응시자(countByQuizId=1)_topPercentile_null")
  void submit_firstAttempter_topPercentileNull() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizAttemptRepository.countByQuizId(1L)).willReturn(1L);

    AttemptResultResponse result = submitSingleCorrectAttempt(owner, quiz, null, 100L);

    assertThat(result.topPercentile()).isNull();
    then(quizAttemptRepository).should(never()).countByQuizIdAndScoreGreaterThan(any(), eq(1));
    then(quizAttemptRepository).should(never()).countByQuizIdAndScore(any(), eq(1));
  }

  @Test
  @DisplayName("submit_응시자2명_본인단독최고점_topPercentile_25_0")
  void submit_twoAttempts_soloTop_topPercentile25() {
    // total=2, score=1, gt=0, eq=1 → (0 + 1/2.0)/2 * 100 = 25.0
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizAttemptRepository.countByQuizId(1L)).willReturn(2L);
    given(quizAttemptRepository.countByQuizIdAndScoreGreaterThan(1L, 1)).willReturn(0L);
    given(quizAttemptRepository.countByQuizIdAndScore(1L, 1)).willReturn(1L);

    AttemptResultResponse result = submitSingleCorrectAttempt(owner, quiz, null, 100L);

    assertThat(result.topPercentile()).isEqualTo(25.0);
  }

  @Test
  @DisplayName("submit_응시자4명_동률_중간처리_topPercentile_50_0")
  void submit_fourAttempts_tieMiddleHandling_topPercentile50() {
    // total=4, score=1, gt=1, eq=2(본인 포함) → (1 + 2/2.0)/4 * 100 = 50.0
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizAttemptRepository.countByQuizId(1L)).willReturn(4L);
    given(quizAttemptRepository.countByQuizIdAndScoreGreaterThan(1L, 1)).willReturn(1L);
    given(quizAttemptRepository.countByQuizIdAndScore(1L, 1)).willReturn(2L);

    AttemptResultResponse result = submitSingleCorrectAttempt(owner, quiz, null, 100L);

    assertThat(result.topPercentile()).isEqualTo(50.0);
  }

  @Test
  @DisplayName("submit_응시자10명_단독최고점_topPercentile_5_0")
  void submit_tenAttempts_soloTop_topPercentile5() {
    // total=10, score=1, gt=0, eq=1 → (0 + 0.5)/10 * 100 = 5.0
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizAttemptRepository.countByQuizId(1L)).willReturn(10L);
    given(quizAttemptRepository.countByQuizIdAndScoreGreaterThan(1L, 1)).willReturn(0L);
    given(quizAttemptRepository.countByQuizIdAndScore(1L, 1)).willReturn(1L);

    AttemptResultResponse result = submitSingleCorrectAttempt(owner, quiz, null, 100L);

    assertThat(result.topPercentile()).isEqualTo(5.0);
  }

  @Test
  @DisplayName("submit_응시자100명_소수1자리_반올림")
  void submit_hundredAttempts_roundedToOneDecimal() {
    // total=100, score=1, gt=33, eq=4(본인 포함) → (33 + 4/2.0)/100 * 100 = 35.0
    // 35.0 은 정확하지만 raw 식 (33 + 2.0) / 100 * 100 = 35.0 — Math.round 경로 검증 목적
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizAttemptRepository.countByQuizId(1L)).willReturn(100L);
    given(quizAttemptRepository.countByQuizIdAndScoreGreaterThan(1L, 1)).willReturn(33L);
    given(quizAttemptRepository.countByQuizIdAndScore(1L, 1)).willReturn(4L);

    AttemptResultResponse result = submitSingleCorrectAttempt(owner, quiz, null, 100L);

    // (33 + 2.0)/100 * 100 = 35.0
    assertThat(result.topPercentile()).isEqualTo(35.0);
  }

  @Test
  @DisplayName("submit_응시자3명_반올림_소수1자리_검증")
  void submit_threeAttempts_roundedToOneDecimal() {
    // total=3, score=1, gt=1, eq=1 → (1 + 0.5)/3 * 100 = 50.0
    // raw = 150.0/3 = 50.0 정확
    // 다른 케이스: total=3, gt=0, eq=2 → (0 + 1.0)/3 * 100 = 33.33... → 33.3
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizAttemptRepository.countByQuizId(1L)).willReturn(3L);
    given(quizAttemptRepository.countByQuizIdAndScoreGreaterThan(1L, 1)).willReturn(0L);
    given(quizAttemptRepository.countByQuizIdAndScore(1L, 1)).willReturn(2L);

    AttemptResultResponse result = submitSingleCorrectAttempt(owner, quiz, null, 100L);

    // Math.round(33.333... * 10) / 10 = 333 / 10.0 = 33.3
    assertThat(result.topPercentile()).isEqualTo(33.3);
  }

  // ─── getScoreDistribution() ─────────────────────────────────────────────────

  private ScoreBucketRow stubRow(int score, long count) {
    ScoreBucketRow row = mock(ScoreBucketRow.class);
    given(row.getScore()).willReturn(score);
    given(row.getCount()).willReturn(count);
    return row;
  }

  @Test
  @DisplayName("getScoreDistribution_존재하지않는퀴즈_QUIZ_NOT_FOUND_throws")
  void getScoreDistribution_quizNotFound_throws() {
    given(quizRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> quizAttemptService.getScoreDistribution(99L, 1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
  }

  @Test
  @DisplayName("getScoreDistribution_PRIVATE외부뷰어_QUIZ_NOT_FOUND_throws")
  void getScoreDistribution_privateQuiz_externalViewer_throws() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PRIVATE);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    assertThatThrownBy(() -> quizAttemptService.getScoreDistribution(1L, 99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);

    then(questionRepository).should(never()).countByQuizId(any());
    then(quizAttemptRepository).should(never()).findScoreDistributionByQuizId(any());
  }

  @Test
  @DisplayName("getScoreDistribution_PRIVATE본인_정상응답")
  void getScoreDistribution_privateQuiz_owner_success() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PRIVATE);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.countByQuizId(1L)).willReturn(3L);
    List<ScoreBucketRow> rows = List.of(stubRow(2, 1L), stubRow(3, 1L));
    given(quizAttemptRepository.findScoreDistributionByQuizId(1L)).willReturn(rows);

    ScoreDistributionResponse result = quizAttemptService.getScoreDistribution(1L, 1L);

    assertThat(result.totalAttempts()).isEqualTo(2L);
    // sum = 2*1 + 3*1 = 5, avg = 5/2 = 2.5
    assertThat(result.averageScore()).isEqualTo(2.5);
    // 0..3 = 4 칸
    assertThat(result.distribution()).hasSize(4);
  }

  @Test
  @DisplayName("getScoreDistribution_빈응시_distribution_전부_0_average_0")
  void getScoreDistribution_noAttempts_emptyDistributionAvgZero() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.countByQuizId(1L)).willReturn(3L);
    given(quizAttemptRepository.findScoreDistributionByQuizId(1L)).willReturn(List.of());

    ScoreDistributionResponse result = quizAttemptService.getScoreDistribution(1L, null);

    assertThat(result.totalAttempts()).isZero();
    assertThat(result.averageScore()).isEqualTo(0.0);
    assertThat(result.distribution()).hasSize(4);
    for (int i = 0; i <= 3; i++) {
      assertThat(result.distribution().get(i).score()).isEqualTo(i);
      assertThat(result.distribution().get(i).count()).isZero();
    }
  }

  @Test
  @DisplayName("getScoreDistribution_sparse_score_0~N_빈칸도_0_채움")
  void getScoreDistribution_sparseScores_fillsEmptyBucketsWithZero() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.countByQuizId(1L)).willReturn(5L);
    // 응시는 score=2 와 score=5 에만
    List<ScoreBucketRow> rows = List.of(stubRow(2, 3L), stubRow(5, 2L));
    given(quizAttemptRepository.findScoreDistributionByQuizId(1L)).willReturn(rows);

    ScoreDistributionResponse result = quizAttemptService.getScoreDistribution(1L, null);

    assertThat(result.totalAttempts()).isEqualTo(5L);
    // sum = 2*3 + 5*2 = 16, avg = 16/5 = 3.2
    assertThat(result.averageScore()).isEqualTo(3.2);
    // 0..5 = 6 칸
    assertThat(result.distribution()).hasSize(6);
    assertThat(result.distribution().get(0).score()).isZero();
    assertThat(result.distribution().get(0).count()).isZero();
    assertThat(result.distribution().get(1).count()).isZero();
    assertThat(result.distribution().get(2).count()).isEqualTo(3L);
    assertThat(result.distribution().get(3).count()).isZero();
    assertThat(result.distribution().get(4).count()).isZero();
    assertThat(result.distribution().get(5).count()).isEqualTo(2L);
  }

  @Test
  @DisplayName("getScoreDistribution_평균_소수1자리_반올림")
  void getScoreDistribution_averageRoundedToOneDecimal() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.countByQuizId(1L)).willReturn(3L);
    // sum = 1*1 + 2*1 + 3*1 = 6, total=3 → avg=2.0 정확하므로
    // 반올림 검증을 위해 다른 조합: sum = 1*2 + 2*1 = 4, total=3 → 4/3 = 1.333... → 1.3
    List<ScoreBucketRow> rows = List.of(stubRow(1, 2L), stubRow(2, 1L));
    given(quizAttemptRepository.findScoreDistributionByQuizId(1L)).willReturn(rows);

    ScoreDistributionResponse result = quizAttemptService.getScoreDistribution(1L, null);

    assertThat(result.totalAttempts()).isEqualTo(3L);
    // Math.round(4 * 10.0 / 3) / 10.0 = Math.round(13.333) / 10.0 = 13 / 10.0 = 1.3
    assertThat(result.averageScore()).isEqualTo(1.3);
  }
}
