package com.ongodmatchu.domain.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.dto.QuestionCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuizCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuizDetailResponse;
import com.ongodmatchu.domain.quiz.dto.QuizResponse;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

  @InjectMocks private QuizService quizService;
  @Mock private QuizRepository quizRepository;
  @Mock private QuestionRepository questionRepository;
  @Mock private UserRepository userRepository;

  private User testUser() {
    User user =
        User.builder()
            .email("user@example.com")
            .nickname("작성자")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    return user;
  }

  private Quiz testQuiz(User user) {
    Quiz quiz = Quiz.builder().user(user).title("퀴즈 제목").category("게임").description("설명").build();
    ReflectionTestUtils.setField(quiz, "id", 1L);
    return quiz;
  }

  @Test
  @DisplayName("카테고리 없이 퀴즈 목록 조회")
  void getQuizList_noCategory() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findAll(any(PageRequest.class))).willReturn(new PageImpl<>(List.of(quiz)));

    var result = quizService.getQuizList(null, PageRequest.of(0, 12));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).title()).isEqualTo("퀴즈 제목");
  }

  @Test
  @DisplayName("카테고리 필터로 퀴즈 목록 조회")
  void getQuizList_withCategory() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findByCategory(any(), any(PageRequest.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));

    var result = quizService.getQuizList("게임", PageRequest.of(0, 12));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).category()).isEqualTo("게임");
  }

  @Test
  @DisplayName("존재하지 않는 퀴즈 상세 조회 시 예외")
  void getQuizDetail_notFound() {
    given(quizRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> quizService.getQuizDetail(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
  }

  @Test
  @DisplayName("퀴즈 상세 조회 성공")
  void getQuizDetail_success() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of());

    QuizDetailResponse result = quizService.getQuizDetail(1L);

    assertThat(result.title()).isEqualTo("퀴즈 제목");
    assertThat(result.questions()).isEmpty();
  }

  @Test
  @DisplayName("퀴즈 생성 성공")
  void createQuiz_success() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(quizRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    given(questionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    QuizCreateRequest request =
        new QuizCreateRequest(
            "새 퀴즈", "설명", "음악", null, List.of(new QuestionCreateRequest(null, "문제1", "정답1")));

    QuizResponse result = quizService.createQuiz(1L, request);

    assertThat(result.title()).isEqualTo("새 퀴즈");
    then(questionRepository).should().save(any());
  }

  @Test
  @DisplayName("플레이 카운트 증가")
  void incrementPlayCount_success() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    quizService.incrementPlayCount(1L);

    assertThat(quiz.getPlayCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("존재하지 않는 사용자로 퀴즈 생성 시 예외")
  void createQuiz_userNotFound() {
    given(userRepository.findById(99L)).willReturn(Optional.empty());

    QuizCreateRequest request =
        new QuizCreateRequest(
            "새 퀴즈", "설명", "음악", null, List.of(new QuestionCreateRequest(null, "문제1", "정답1")));

    assertThatThrownBy(() -> quizService.createQuiz(99L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);

    then(quizRepository).should(times(0)).save(any());
    then(questionRepository).should(times(0)).save(any());
  }

  @Test
  @DisplayName("여러 질문 포함 퀴즈 생성 시 orderNum이 순차적으로 부여됨")
  void createQuiz_multipleQuestions() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(quizRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    given(questionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    QuizCreateRequest request =
        new QuizCreateRequest(
            "멀티 퀴즈",
            "설명",
            "음악",
            null,
            List.of(
                new QuestionCreateRequest(null, "문제1", "정답1"),
                new QuestionCreateRequest(null, "문제2", "정답2"),
                new QuestionCreateRequest(null, "문제3", "정답3")));

    quizService.createQuiz(1L, request);

    ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
    then(questionRepository).should(times(3)).save(captor.capture());

    List<Question> saved = captor.getAllValues();
    assertThat(saved).hasSize(3);
    assertThat(saved.get(0).getOrderNum()).isEqualTo(1);
    assertThat(saved.get(1).getOrderNum()).isEqualTo(2);
    assertThat(saved.get(2).getOrderNum()).isEqualTo(3);
  }

  @Test
  @DisplayName("존재하지 않는 퀴즈 플레이 카운트 증가 시 예외")
  void incrementPlayCount_quizNotFound() {
    given(quizRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> quizService.incrementPlayCount(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
  }
}
