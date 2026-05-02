package com.ongodmatchu.domain.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.dto.CategoryResponse;
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
import com.ongodmatchu.infra.s3.S3Service;
import com.ongodmatchu.infra.s3.ViewUrlResponse;
import java.time.Instant;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

  @InjectMocks private QuizService quizService;
  @Mock private QuizRepository quizRepository;
  @Mock private QuestionRepository questionRepository;
  @Mock private UserRepository userRepository;
  @Mock private S3Service s3Service;

  @BeforeEach
  void setUp() {
    lenient().when(s3Service.batchPresignViewUrls(any())).thenReturn(Map.of());
    lenient()
        .when(s3Service.generateViewUrl(anyString()))
        .thenAnswer(
            inv ->
                new ViewUrlResponse(
                    "https://signed.example/" + inv.getArgument(0),
                    inv.getArgument(0),
                    3600L,
                    Instant.now().plusSeconds(3600)));
  }

  private User testUser() {
    User user =
        User.builder()
            .email("user@example.com")
            .nickname("작성자")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    ReflectionTestUtils.setField(user, "publicId", UUID.randomUUID());
    return user;
  }

  private Quiz testQuiz(User user) {
    Quiz quiz = Quiz.builder().user(user).title("퀴즈 제목").category("game").description("설명").build();
    ReflectionTestUtils.setField(quiz, "id", 1L);
    ReflectionTestUtils.setField(quiz, "publicId", UUID.randomUUID());
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

    var result = quizService.getQuizList("game", PageRequest.of(0, 12));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).category()).isEqualTo("game");
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
  @DisplayName("퀴즈 생성 성공 — key 검증 + 영문 카테고리")
  void createQuiz_success() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(quizRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    given(questionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    QuizCreateRequest request =
        new QuizCreateRequest(
            "새 퀴즈",
            "설명",
            "music",
            null,
            List.of(new QuestionCreateRequest(null, null, "문제1", "정답1")));

    QuizResponse result = quizService.createQuiz(1L, request);

    assertThat(result.title()).isEqualTo("새 퀴즈");
    then(questionRepository).should().save(any());
  }

  @Test
  @DisplayName("문제의 imageKey / answerImageKey 가 그대로 저장된다 — S3 검증 통과 후")
  void createQuiz_persistsImageKeys() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(quizRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    given(questionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    QuizCreateRequest request =
        new QuizCreateRequest(
            "이미지 퀴즈",
            "설명",
            "entertainment",
            "quiz-images/uid/thumb.png",
            List.of(
                new QuestionCreateRequest(
                    "quiz-images/uid/q.png", "quiz-images/uid/a.png", "문제1", "정답1")));

    quizService.createQuiz(1L, request);

    ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
    then(questionRepository).should().save(captor.capture());
    Question saved = captor.getValue();
    assertThat(saved.getImageKey()).isEqualTo("quiz-images/uid/q.png");
    assertThat(saved.getAnswerImageKey()).isEqualTo("quiz-images/uid/a.png");
    then(s3Service).should().verifyKeyOwnedAndCompleted(1L, "quiz-images/uid/thumb.png");
    then(s3Service).should().verifyKeyOwnedAndCompleted(1L, "quiz-images/uid/q.png");
    then(s3Service).should().verifyKeyOwnedAndCompleted(1L, "quiz-images/uid/a.png");
  }

  @Test
  @DisplayName("answerImageKey 와 imageKey 가 동일하면 검증은 한 번만 호출된다")
  void createQuiz_sameKeyForBoth() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(quizRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    given(questionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    String key = "quiz-images/uid/same.png";
    QuizCreateRequest request =
        new QuizCreateRequest(
            "동일 key 퀴즈",
            null,
            "etc",
            null,
            List.of(new QuestionCreateRequest(key, key, "문제1", "정답1")));

    quizService.createQuiz(1L, request);

    ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
    then(questionRepository).should().save(captor.capture());
    assertThat(captor.getValue().getImageKey()).isEqualTo(key);
    assertThat(captor.getValue().getAnswerImageKey()).isEqualTo(key);
    then(s3Service).should(times(1)).verifyKeyOwnedAndCompleted(1L, key);
  }

  @Test
  @DisplayName("화이트리스트 외 카테고리로 생성 시 INVALID_CATEGORY 예외")
  void createQuiz_invalidCategory() {
    QuizCreateRequest request =
        new QuizCreateRequest(
            "잘못된 카테고리",
            "설명",
            "역사",
            null,
            List.of(new QuestionCreateRequest(null, null, "문제1", "정답1")));

    assertThatThrownBy(() -> quizService.createQuiz(1L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_CATEGORY);

    then(userRepository).should(times(0)).findById(any());
    then(quizRepository).should(times(0)).save(any());
    then(questionRepository).should(times(0)).save(any());
    then(s3Service).should(times(0)).verifyKeyOwnedAndCompleted(anyLong(), anyString());
  }

  @Test
  @DisplayName("카테고리 목록을 영문 키 + 라벨로 반환한다")
  void getCategories_success() {
    List<CategoryResponse> categories = quizService.getCategories();

    assertThat(categories).hasSize(9);
    assertThat(categories)
        .extracting(CategoryResponse::key)
        .containsExactly(
            "entertainment",
            "movie",
            "drama",
            "anime",
            "game",
            "music",
            "sports",
            "general",
            "etc");
    assertThat(categories.get(3).label()).isEqualTo("애니메이션");
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
            "새 퀴즈",
            "설명",
            "music",
            null,
            List.of(new QuestionCreateRequest(null, null, "문제1", "정답1")));

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
            "music",
            null,
            List.of(
                new QuestionCreateRequest(null, null, "문제1", "정답1"),
                new QuestionCreateRequest(null, null, "문제2", "정답2"),
                new QuestionCreateRequest(null, null, "문제3", "정답3")));

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
