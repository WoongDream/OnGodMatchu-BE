package com.ongodmatchu.domain.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.ongodmatchu.domain.comment.repository.QuizCommentRepository;
import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.dto.CategoryResponse;
import com.ongodmatchu.domain.quiz.dto.MyQuizListItemResponse;
import com.ongodmatchu.domain.quiz.dto.QuestionCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuizCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuizDetailResponse;
import com.ongodmatchu.domain.quiz.dto.QuizResponse;
import com.ongodmatchu.domain.quiz.dto.QuizSort;
import com.ongodmatchu.domain.quiz.dto.QuizUpdateRequest;
import com.ongodmatchu.domain.quiz.dto.VisibilityFilter;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.quiz.repository.QuizAttemptRepository;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.quiz.repository.QuizStarRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

  @InjectMocks private QuizService quizService;
  @Mock private QuizRepository quizRepository;
  @Mock private QuestionRepository questionRepository;
  @Mock private UserRepository userRepository;
  @Mock private QuizStarRepository quizStarRepository;
  @Mock private QuizAttemptRepository quizAttemptRepository;
  @Mock private QuizCommentRepository quizCommentRepository;
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
    Quiz quiz =
        Quiz.builder()
            .user(user)
            .title("퀴즈 제목")
            .category("game")
            .description("설명")
            .visibility(QuizVisibility.PUBLIC)
            .build();
    ReflectionTestUtils.setField(quiz, "id", 1L);
    ReflectionTestUtils.setField(quiz, "publicId", UUID.randomUUID());
    return quiz;
  }

  @Test
  @DisplayName("카테고리 없이 퀴즈 목록 조회 — PUBLIC 만")
  void getQuizList_noCategory() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findByVisibility(eq(QuizVisibility.PUBLIC), any(PageRequest.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));

    var result = quizService.getQuizList(null, PageRequest.of(0, 12));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).title()).isEqualTo("퀴즈 제목");
    then(quizRepository)
        .should()
        .findByVisibility(eq(QuizVisibility.PUBLIC), any(PageRequest.class));
  }

  @Test
  @DisplayName("카테고리 필터로 퀴즈 목록 조회 — PUBLIC 만")
  void getQuizList_withCategory() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(
            quizRepository.findByCategoryAndVisibility(
                any(), eq(QuizVisibility.PUBLIC), any(PageRequest.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));

    var result = quizService.getQuizList("game", PageRequest.of(0, 12));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).category()).isEqualTo("game");
    then(quizRepository)
        .should()
        .findByCategoryAndVisibility(eq("game"), eq(QuizVisibility.PUBLIC), any(PageRequest.class));
  }

  @Test
  @DisplayName("존재하지 않는 퀴즈 상세 조회 시 예외")
  void getQuizDetail_notFound() {
    given(quizRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> quizService.getQuizDetail(99L, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
  }

  @Test
  @DisplayName("퀴즈 상세 조회 성공 — PUBLIC 은 비로그인도 조회 가능")
  void getQuizDetail_success() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of());

    QuizDetailResponse result = quizService.getQuizDetail(1L, null);

    assertThat(result.title()).isEqualTo("퀴즈 제목");
    assertThat(result.questions()).isEmpty();
    assertThat(result.visibility()).isEqualTo(QuizVisibility.PUBLIC);
  }

  @Test
  @DisplayName("PRIVATE 퀴즈 단건 조회 — 외부 뷰어는 QUIZ_NOT_FOUND")
  void getQuizDetail_private_externalViewer_throwsNotFound() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    quiz.changeVisibility(QuizVisibility.PRIVATE);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    assertThatThrownBy(() -> quizService.getQuizDetail(1L, 99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
  }

  @Test
  @DisplayName("PRIVATE 퀴즈 단건 조회 — 비로그인도 QUIZ_NOT_FOUND")
  void getQuizDetail_private_anonymousViewer_throwsNotFound() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    quiz.changeVisibility(QuizVisibility.PRIVATE);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    assertThatThrownBy(() -> quizService.getQuizDetail(1L, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
  }

  @Test
  @DisplayName("PRIVATE 퀴즈 단건 조회 — 본인은 정상 조회")
  void getQuizDetail_private_owner_success() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    quiz.changeVisibility(QuizVisibility.PRIVATE);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of());

    QuizDetailResponse result = quizService.getQuizDetail(1L, 1L);

    assertThat(result.title()).isEqualTo("퀴즈 제목");
    assertThat(result.visibility()).isEqualTo(QuizVisibility.PRIVATE);
  }

  @Test
  @DisplayName("Quiz 빌더는 visibility 미지정 시 PRIVATE 기본값")
  void quizBuilder_defaultsVisibilityToPrivate() {
    User user = testUser();
    Quiz quiz = Quiz.builder().user(user).title("t").category("game").build();

    assertThat(quiz.getVisibility()).isEqualTo(QuizVisibility.PRIVATE);
  }

  @Test
  @DisplayName("createQuiz_visibility_미지정시_PRIVATE로저장")
  void createQuiz_visibilityDefaultsToPrivate() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(quizRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    given(questionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    QuizCreateRequest request =
        new QuizCreateRequest(
            "기본비공개",
            null,
            "etc",
            null,
            null,
            List.of(new QuestionCreateRequest(null, null, "문제1", "정답1")));

    QuizResponse result = quizService.createQuiz(1L, request);

    assertThat(result.visibility()).isEqualTo(QuizVisibility.PRIVATE);
  }

  @Test
  @DisplayName("createQuiz_visibility_PUBLIC지정시_PUBLIC로저장")
  void createQuiz_visibilityPublic() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(quizRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    given(questionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    QuizCreateRequest request =
        new QuizCreateRequest(
            "공개퀴즈",
            null,
            "etc",
            null,
            QuizVisibility.PUBLIC,
            List.of(new QuestionCreateRequest(null, null, "문제1", "정답1")));

    QuizResponse result = quizService.createQuiz(1L, request);

    assertThat(result.visibility()).isEqualTo(QuizVisibility.PUBLIC);
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
            null,
            List.of(new QuestionCreateRequest(null, null, "문제1", "정답1")));

    QuizResponse result = quizService.createQuiz(1L, request);

    assertThat(result.title()).isEqualTo("새 퀴즈");
    assertThat(result.visibility()).isEqualTo(QuizVisibility.PRIVATE);
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
            null,
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
  @DisplayName("incrementPlayCount_PUBLIC퀴즈_비로그인_정상증가")
  void incrementPlayCount_success() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    quizService.incrementPlayCount(1L, null);

    assertThat(quiz.getPlayCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("incrementPlayCount_PRIVATE퀴즈_외부유저_QUIZ_NOT_FOUND")
  void incrementPlayCount_privateQuiz_externalUser_throws() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    quiz.changeVisibility(QuizVisibility.PRIVATE);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    assertThatThrownBy(() -> quizService.incrementPlayCount(1L, 99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
    assertThat(quiz.getPlayCount()).isZero();
  }

  @Test
  @DisplayName("incrementPlayCount_PRIVATE퀴즈_본인_정상증가")
  void incrementPlayCount_privateQuiz_owner() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    quiz.changeVisibility(QuizVisibility.PRIVATE);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    quizService.incrementPlayCount(1L, 1L);

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
  @DisplayName("incrementShareCount_PUBLIC퀴즈_비로그인_정상증가")
  void incrementShareCount_publicQuiz_anonymous() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    quizService.incrementShareCount(1L, null);

    assertThat(quiz.getShareCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("incrementShareCount_PRIVATE퀴즈_외부유저_QUIZ_NOT_FOUND")
  void incrementShareCount_privateQuiz_externalUser_throws() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    quiz.changeVisibility(QuizVisibility.PRIVATE);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    assertThatThrownBy(() -> quizService.incrementShareCount(1L, 99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
    assertThat(quiz.getShareCount()).isZero();
  }

  @Test
  @DisplayName("incrementShareCount_PRIVATE퀴즈_본인_정상증가")
  void incrementShareCount_privateQuiz_owner() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    quiz.changeVisibility(QuizVisibility.PRIVATE);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    quizService.incrementShareCount(1L, 1L);

    assertThat(quiz.getShareCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("존재하지 않는 퀴즈 플레이 카운트 증가 시 예외")
  void incrementPlayCount_quizNotFound() {
    given(quizRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> quizService.incrementPlayCount(99L, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
  }

  // ============ getProfileStats ============

  private com.ongodmatchu.domain.quiz.repository.QuizRepository.QuizAggregateRow stubAggregateRow(
      long quizCount, long plays, long stars, long comments, long shares) {
    return new com.ongodmatchu.domain.quiz.repository.QuizRepository.QuizAggregateRow() {
      @Override
      public long getQuizCount() {
        return quizCount;
      }

      @Override
      public long getPlays() {
        return plays;
      }

      @Override
      public long getStars() {
        return stars;
      }

      @Override
      public long getComments() {
        return comments;
      }

      @Override
      public long getShares() {
        return shares;
      }
    };
  }

  @Test
  @DisplayName("getProfileStats_정상_aggregateRow매핑+weeklyPlay0+avgCorrectRate_null")
  void getProfileStats_returnsAggregatedRow() {
    given(quizRepository.aggregateByUserId(1L)).willReturn(stubAggregateRow(5, 100, 30, 12, 7));
    given(quizAttemptRepository.countWeeklyPlaysOfQuizzesOwnedBy(eq(1L), any())).willReturn(0L);
    given(quizAttemptRepository.perQuizCorrectRatesOwnedBy(1L)).willReturn(List.of());

    com.ongodmatchu.domain.user.dto.ProfileStatsResponse stats = quizService.getProfileStats(1L);

    assertThat(stats.totalQuizCount()).isEqualTo(5L);
    assertThat(stats.totalPlayCount()).isEqualTo(100L);
    assertThat(stats.totalStarCount()).isEqualTo(30L);
    assertThat(stats.totalCommentCount()).isEqualTo(12L);
    assertThat(stats.totalShareCount()).isEqualTo(7L);
    assertThat(stats.weeklyPlayCount()).isZero();
    assertThat(stats.avgCorrectRate()).isNull();
  }

  @Test
  @DisplayName("getProfileStats_weeklyPlayCount_퀴즈attempt기반_정상집계")
  void getProfileStats_weeklyPlayCount_aggregated() {
    given(quizRepository.aggregateByUserId(1L)).willReturn(stubAggregateRow(3, 50, 10, 5, 2));
    given(quizAttemptRepository.countWeeklyPlaysOfQuizzesOwnedBy(eq(1L), any())).willReturn(12L);
    given(quizAttemptRepository.perQuizCorrectRatesOwnedBy(1L)).willReturn(List.of());

    com.ongodmatchu.domain.user.dto.ProfileStatsResponse stats = quizService.getProfileStats(1L);

    assertThat(stats.weeklyPlayCount()).isEqualTo(12L);
    assertThat(stats.avgCorrectRate()).isNull();
  }

  @Test
  @DisplayName("getProfileStats_avgCorrectRate_퀴즈1개_단순값반환")
  void getProfileStats_avgCorrectRate_singleQuiz() {
    given(quizRepository.aggregateByUserId(1L)).willReturn(stubAggregateRow(1, 10, 0, 0, 0));
    given(quizAttemptRepository.countWeeklyPlaysOfQuizzesOwnedBy(eq(1L), any())).willReturn(5L);
    given(quizAttemptRepository.perQuizCorrectRatesOwnedBy(1L)).willReturn(List.of(80.0));

    com.ongodmatchu.domain.user.dto.ProfileStatsResponse stats = quizService.getProfileStats(1L);

    assertThat(stats.avgCorrectRate()).isEqualTo(80.0);
  }

  @Test
  @DisplayName("getProfileStats_avgCorrectRate_퀴즈복수_단순평균")
  void getProfileStats_avgCorrectRate_multipleQuizzes_simpleAverage() {
    given(quizRepository.aggregateByUserId(1L)).willReturn(stubAggregateRow(3, 30, 5, 2, 1));
    given(quizAttemptRepository.countWeeklyPlaysOfQuizzesOwnedBy(eq(1L), any())).willReturn(3L);
    // 퀴즈A=60, 퀴즈B=80, 퀴즈C=100 → 평균 80
    given(quizAttemptRepository.perQuizCorrectRatesOwnedBy(1L))
        .willReturn(List.of(60.0, 80.0, 100.0));

    com.ongodmatchu.domain.user.dto.ProfileStatsResponse stats = quizService.getProfileStats(1L);

    assertThat(stats.avgCorrectRate()).isEqualTo(80.0);
  }

  @Test
  @DisplayName("getProfileStats_perQuizRates비어있으면_avgCorrectRate_null")
  void getProfileStats_emptyPerQuizRates_avgCorrectRateIsNull() {
    given(quizRepository.aggregateByUserId(1L)).willReturn(stubAggregateRow(2, 0, 0, 0, 0));
    given(quizAttemptRepository.countWeeklyPlaysOfQuizzesOwnedBy(eq(1L), any())).willReturn(0L);
    given(quizAttemptRepository.perQuizCorrectRatesOwnedBy(1L)).willReturn(List.of());

    com.ongodmatchu.domain.user.dto.ProfileStatsResponse stats = quizService.getProfileStats(1L);

    assertThat(stats.avgCorrectRate()).isNull();
  }

  // ============ getMyQuizList ============

  @Test
  @DisplayName("getMyQuizList_본인퀴즈목록_ALL_LATEST_정상반환")
  void getMyQuizList_returnsPaginatedResults() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findByUserId(eq(1L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));

    Page<MyQuizListItemResponse> result =
        quizService.getMyQuizList(1L, VisibilityFilter.ALL, QuizSort.LATEST, PageRequest.of(0, 12));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).title()).isEqualTo("퀴즈 제목");
    then(quizRepository).should().findByUserId(eq(1L), any(Pageable.class));
  }

  @Test
  @DisplayName("getMyQuizList_PUBLIC필터_findByUserIdAndVisibility호출")
  void getMyQuizList_publicFilter_callsVisibilityRepo() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(
            quizRepository.findByUserIdAndVisibility(
                eq(1L), eq(QuizVisibility.PUBLIC), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));

    Page<MyQuizListItemResponse> result =
        quizService.getMyQuizList(
            1L, VisibilityFilter.PUBLIC, QuizSort.LATEST, PageRequest.of(0, 12));

    assertThat(result.getContent()).hasSize(1);
    then(quizRepository)
        .should()
        .findByUserIdAndVisibility(eq(1L), eq(QuizVisibility.PUBLIC), any(Pageable.class));
    then(quizRepository).should(never()).findByUserId(anyLong(), any(Pageable.class));
  }

  @Test
  @DisplayName("getMyQuizList_PRIVATE필터_findByUserIdAndVisibility호출")
  void getMyQuizList_privateFilter_callsVisibilityRepo() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    quiz.changeVisibility(QuizVisibility.PRIVATE);
    given(
            quizRepository.findByUserIdAndVisibility(
                eq(1L), eq(QuizVisibility.PRIVATE), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));

    Page<MyQuizListItemResponse> result =
        quizService.getMyQuizList(
            1L, VisibilityFilter.PRIVATE, QuizSort.LATEST, PageRequest.of(0, 12));

    assertThat(result.getContent()).hasSize(1);
    then(quizRepository)
        .should()
        .findByUserIdAndVisibility(eq(1L), eq(QuizVisibility.PRIVATE), any(Pageable.class));
  }

  @Test
  @DisplayName("getMyQuizList_퀴즈없음_빈페이지반환")
  void getMyQuizList_noQuizzes_returnsEmptyPage() {
    given(quizRepository.findByUserId(eq(1L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    Page<MyQuizListItemResponse> result =
        quizService.getMyQuizList(1L, VisibilityFilter.ALL, QuizSort.LATEST, PageRequest.of(0, 12));

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
  }

  @Test
  @DisplayName("getMyQuizList_썸네일키있는퀴즈_presign매핑")
  void getMyQuizList_withThumbnailKey_mapsPresignedUrl() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    ReflectionTestUtils.setField(quiz, "thumbnailKey", "quiz-images/uid/thumb.png");
    given(quizRepository.findByUserId(eq(1L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));
    given(s3Service.batchPresignViewUrls(List.of("quiz-images/uid/thumb.png")))
        .willReturn(Map.of("quiz-images/uid/thumb.png", "https://signed.example/thumb.png"));

    Page<MyQuizListItemResponse> result =
        quizService.getMyQuizList(1L, VisibilityFilter.ALL, QuizSort.LATEST, PageRequest.of(0, 12));

    assertThat(result.getContent().get(0).thumbnailUrl())
        .isEqualTo("https://signed.example/thumb.png");
  }

  @Test
  @DisplayName("getMyQuizList_correctRateByQuizIds결과_quizId별correctRate매핑")
  void getMyQuizList_correctRateByQuizIds_mapsToItem() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    // quizId=1 에 대한 correctRate 75.0
    com.ongodmatchu.domain.quiz.repository.QuizAttemptRepository.QuizCorrectRateRow rateRow =
        new com.ongodmatchu.domain.quiz.repository.QuizAttemptRepository.QuizCorrectRateRow() {
          @Override
          public Long getQuizId() {
            return 1L;
          }

          @Override
          public Double getRate() {
            return 75.0;
          }
        };
    given(quizRepository.findByUserId(eq(1L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));
    given(quizAttemptRepository.correctRateByQuizIds(List.of(1L))).willReturn(List.of(rateRow));

    Page<MyQuizListItemResponse> result =
        quizService.getMyQuizList(1L, VisibilityFilter.ALL, QuizSort.LATEST, PageRequest.of(0, 12));

    assertThat(result.getContent().get(0).correctRate()).isEqualTo(75.0);
  }

  @Test
  @DisplayName("getMyQuizList_correctRateByQuizIds결과없음_correctRate_null")
  void getMyQuizList_noCorrectRateResult_correctRateIsNull() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findByUserId(eq(1L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));
    given(quizAttemptRepository.correctRateByQuizIds(List.of(1L))).willReturn(List.of());

    Page<MyQuizListItemResponse> result =
        quizService.getMyQuizList(1L, VisibilityFilter.ALL, QuizSort.LATEST, PageRequest.of(0, 12));

    assertThat(result.getContent().get(0).correctRate()).isNull();
  }

  @Test
  @DisplayName("getMyQuizList_퀴즈없으면_correctRateByQuizIds미호출")
  void getMyQuizList_emptyPage_correctRateQueryNotCalled() {
    given(quizRepository.findByUserId(eq(1L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    quizService.getMyQuizList(1L, VisibilityFilter.ALL, QuizSort.LATEST, PageRequest.of(0, 12));

    then(quizAttemptRepository).should(never()).correctRateByQuizIds(any());
  }

  @Test
  @DisplayName("getMyQuizList_size50초과요청_50으로cap")
  void getMyQuizList_pageSize_cappedAt50() {
    given(quizRepository.findByUserId(eq(1L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    quizService.getMyQuizList(1L, VisibilityFilter.ALL, QuizSort.LATEST, PageRequest.of(0, 200));

    org.mockito.ArgumentCaptor<Pageable> captor =
        org.mockito.ArgumentCaptor.forClass(Pageable.class);
    then(quizRepository).should().findByUserId(eq(1L), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(50);
  }

  // ============ getQuizListByPublicId ============

  @Test
  @DisplayName("getQuizListByPublicId_publicId없는사용자_USER_NOT_FOUND예외")
  void getQuizListByPublicId_userNotFound_throwsException() {
    UUID unknownId = UUID.randomUUID();
    given(userRepository.findByPublicId(unknownId)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                quizService.getQuizListByPublicId(
                    unknownId, null, QuizSort.LATEST, PageRequest.of(0, 12)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("getQuizListByPublicId_공개프로필_비로그인_PUBLIC만반환")
  void getQuizListByPublicId_publicProfile_anonymousViewer_returnsList() {
    User author = testUser();
    Quiz quiz = testQuiz(author);
    UUID authorPublicId = author.getPublicId();
    given(userRepository.findByPublicId(authorPublicId)).willReturn(Optional.of(author));
    given(
            quizRepository.findByUserIdAndVisibility(
                eq(1L), eq(QuizVisibility.PUBLIC), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));

    Page<MyQuizListItemResponse> result =
        quizService.getQuizListByPublicId(
            authorPublicId, null, QuizSort.LATEST, PageRequest.of(0, 12));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).title()).isEqualTo("퀴즈 제목");
    then(quizRepository)
        .should()
        .findByUserIdAndVisibility(eq(1L), eq(QuizVisibility.PUBLIC), any(Pageable.class));
  }

  @Test
  @DisplayName("getQuizListByPublicId_공개프로필_외부뷰어_PUBLIC만반환")
  void getQuizListByPublicId_publicProfile_externalViewer_returnsList() {
    User author = testUser();
    Quiz quiz = testQuiz(author);
    UUID authorPublicId = author.getPublicId();
    given(userRepository.findByPublicId(authorPublicId)).willReturn(Optional.of(author));
    given(
            quizRepository.findByUserIdAndVisibility(
                eq(1L), eq(QuizVisibility.PUBLIC), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));

    Page<MyQuizListItemResponse> result =
        quizService.getQuizListByPublicId(
            authorPublicId, 99L, QuizSort.LATEST, PageRequest.of(0, 12));

    assertThat(result.getContent()).hasSize(1);
    then(quizRepository)
        .should()
        .findByUserIdAndVisibility(eq(1L), eq(QuizVisibility.PUBLIC), any(Pageable.class));
    then(quizRepository).should(never()).findByUserId(anyLong(), any(Pageable.class));
  }

  @Test
  @DisplayName("getQuizListByPublicId_비공개프로필_본인_PRIVATE포함_전체반환")
  void getQuizListByPublicId_privateProfile_owner_returnsList() {
    User author = testUser();
    author.updateProfilePublic(false);
    Quiz quiz = testQuiz(author);
    quiz.changeVisibility(QuizVisibility.PRIVATE);
    UUID authorPublicId = author.getPublicId();
    given(userRepository.findByPublicId(authorPublicId)).willReturn(Optional.of(author));
    given(quizRepository.findByUserId(eq(1L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));

    Page<MyQuizListItemResponse> result =
        quizService.getQuizListByPublicId(
            authorPublicId, 1L, QuizSort.LATEST, PageRequest.of(0, 12));

    assertThat(result.getContent()).hasSize(1);
    then(quizRepository).should().findByUserId(eq(1L), any(Pageable.class));
  }

  @Test
  @DisplayName("getQuizListByPublicId_비공개프로필_외부뷰어_빈페이지반환")
  void getQuizListByPublicId_privateProfile_externalViewer_returnsEmptyPage() {
    User author = testUser();
    author.updateProfilePublic(false);
    UUID authorPublicId = author.getPublicId();
    given(userRepository.findByPublicId(authorPublicId)).willReturn(Optional.of(author));

    Page<MyQuizListItemResponse> result =
        quizService.getQuizListByPublicId(
            authorPublicId, 99L, QuizSort.LATEST, PageRequest.of(0, 12));

    assertThat(result.getContent()).isEmpty();
    then(quizRepository).should(never()).findByUserId(anyLong(), any(Pageable.class));
    then(quizRepository)
        .should(never())
        .findByUserIdAndVisibility(anyLong(), any(), any(Pageable.class));
  }

  @Test
  @DisplayName("getQuizListByPublicId_비공개프로필_비로그인_빈페이지반환")
  void getQuizListByPublicId_privateProfile_anonymousViewer_returnsEmptyPage() {
    User author = testUser();
    author.updateProfilePublic(false);
    UUID authorPublicId = author.getPublicId();
    given(userRepository.findByPublicId(authorPublicId)).willReturn(Optional.of(author));

    Page<MyQuizListItemResponse> result =
        quizService.getQuizListByPublicId(
            authorPublicId, null, QuizSort.LATEST, PageRequest.of(0, 12));

    assertThat(result.getContent()).isEmpty();
    then(quizRepository).should(never()).findByUserId(anyLong(), any(Pageable.class));
    then(quizRepository)
        .should(never())
        .findByUserIdAndVisibility(anyLong(), any(), any(Pageable.class));
  }

  // ============ updateQuiz ============

  @Test
  @DisplayName("updateQuiz_퀴즈미존재_QUIZ_NOT_FOUND예외")
  void updateQuiz_quizNotFound_throwsException() {
    given(quizRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                quizService.updateQuiz(
                    1L, 99L, new QuizUpdateRequest(null, null, null, null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
  }

  @Test
  @DisplayName("updateQuiz_본인아닌사용자_QUIZ_FORBIDDEN예외")
  void updateQuiz_notOwner_throwsForbidden() {
    User owner = testUser();
    Quiz quiz = testQuiz(owner);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    assertThatThrownBy(
            () ->
                quizService.updateQuiz(
                    999L, 1L, new QuizUpdateRequest(null, null, null, null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_FORBIDDEN);
  }

  @Test
  @DisplayName("updateQuiz_title변경_필드업데이트")
  void updateQuiz_updateTitle_updatesField() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    QuizResponse result =
        quizService.updateQuiz(1L, 1L, new QuizUpdateRequest("새제목", null, null, null, null));

    assertThat(result.title()).isEqualTo("새제목");
    assertThat(quiz.getTitle()).isEqualTo("새제목");
  }

  @Test
  @DisplayName("updateQuiz_description변경_필드업데이트")
  void updateQuiz_updateDescription_updatesField() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    QuizResponse result =
        quizService.updateQuiz(1L, 1L, new QuizUpdateRequest(null, "새설명", null, null, null));

    assertThat(result.description()).isEqualTo("새설명");
    assertThat(quiz.getDescription()).isEqualTo("새설명");
  }

  @Test
  @DisplayName("updateQuiz_유효한카테고리변경_필드업데이트")
  void updateQuiz_updateCategory_validKey_updatesField() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    QuizResponse result =
        quizService.updateQuiz(1L, 1L, new QuizUpdateRequest(null, null, "music", null, null));

    assertThat(result.category()).isEqualTo("music");
    assertThat(quiz.getCategory()).isEqualTo("music");
  }

  @Test
  @DisplayName("updateQuiz_무효한카테고리_INVALID_CATEGORY예외")
  void updateQuiz_invalidCategory_throwsException() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    assertThatThrownBy(
            () ->
                quizService.updateQuiz(1L, 1L, new QuizUpdateRequest(null, null, "역사", null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_CATEGORY);
  }

  @Test
  @DisplayName("updateQuiz_다른썸네일키로변경_verify호출+이전키삭제")
  void updateQuiz_newThumbnailKey_verifiesAndDeletesPrevious() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    ReflectionTestUtils.setField(quiz, "thumbnailKey", "old-key.png");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    willDoNothing().given(s3Service).verifyKeyOwnedAndCompleted(1L, "new-key.png");
    willDoNothing().given(s3Service).deleteQuietly("old-key.png");

    quizService.updateQuiz(1L, 1L, new QuizUpdateRequest(null, null, null, "new-key.png", null));

    then(s3Service).should().verifyKeyOwnedAndCompleted(1L, "new-key.png");
    then(s3Service).should().deleteQuietly("old-key.png");
    assertThat(quiz.getThumbnailKey()).isEqualTo("new-key.png");
  }

  @Test
  @DisplayName("updateQuiz_이전썸네일없고새키로변경_verify호출_delete미호출")
  void updateQuiz_newThumbnailKey_noPreviousKey_verifiesButDoesNotDelete() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    willDoNothing().given(s3Service).verifyKeyOwnedAndCompleted(1L, "new-key.png");

    quizService.updateQuiz(1L, 1L, new QuizUpdateRequest(null, null, null, "new-key.png", null));

    then(s3Service).should().verifyKeyOwnedAndCompleted(1L, "new-key.png");
    then(s3Service).should(never()).deleteQuietly(anyString());
  }

  @Test
  @DisplayName("updateQuiz_동일썸네일키_verify호출안함_delete호출안함")
  void updateQuiz_sameThumbnailKey_noVerifyNoDelete() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    ReflectionTestUtils.setField(quiz, "thumbnailKey", "same-key.png");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    quizService.updateQuiz(1L, 1L, new QuizUpdateRequest(null, null, null, "same-key.png", null));

    then(s3Service).should(never()).verifyKeyOwnedAndCompleted(anyLong(), anyString());
    then(s3Service).should(never()).deleteQuietly(anyString());
  }

  @Test
  @DisplayName("updateQuiz_visibility_PUBLIC으로토글")
  void updateQuiz_visibility_togglesToPublic() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    quiz.changeVisibility(QuizVisibility.PRIVATE);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    QuizResponse result =
        quizService.updateQuiz(
            1L, 1L, new QuizUpdateRequest(null, null, null, null, QuizVisibility.PUBLIC));

    assertThat(result.visibility()).isEqualTo(QuizVisibility.PUBLIC);
    assertThat(quiz.getVisibility()).isEqualTo(QuizVisibility.PUBLIC);
  }

  @Test
  @DisplayName("updateQuiz_visibility_PRIVATE으로토글")
  void updateQuiz_visibility_togglesToPrivate() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    QuizResponse result =
        quizService.updateQuiz(
            1L, 1L, new QuizUpdateRequest(null, null, null, null, QuizVisibility.PRIVATE));

    assertThat(result.visibility()).isEqualTo(QuizVisibility.PRIVATE);
    assertThat(quiz.getVisibility()).isEqualTo(QuizVisibility.PRIVATE);
  }

  @Test
  @DisplayName("updateQuiz_null필드_아무것도변경안함")
  void updateQuiz_allNullFields_noChanges() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    QuizResponse result =
        quizService.updateQuiz(1L, 1L, new QuizUpdateRequest(null, null, null, null, null));

    assertThat(result.title()).isEqualTo("퀴즈 제목");
    assertThat(result.category()).isEqualTo("game");
    then(s3Service).should(never()).verifyKeyOwnedAndCompleted(anyLong(), anyString());
    then(s3Service).should(never()).deleteQuietly(anyString());
  }

  // ============ deleteQuiz ============

  @Test
  @DisplayName("deleteQuiz_퀴즈미존재_QUIZ_NOT_FOUND예외")
  void deleteQuiz_quizNotFound_throwsException() {
    given(quizRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> quizService.deleteQuiz(1L, 99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
  }

  @Test
  @DisplayName("deleteQuiz_본인아닌사용자_QUIZ_FORBIDDEN예외")
  void deleteQuiz_notOwner_throwsForbidden() {
    User owner = testUser();
    Quiz quiz = testQuiz(owner);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    assertThatThrownBy(() -> quizService.deleteQuiz(999L, 1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_FORBIDDEN);
  }

  @Test
  @DisplayName("deleteQuiz_정상삭제_questionDeleteByQuizId+quizDelete호출")
  void deleteQuiz_success_deletesQuestionsAndQuiz() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of());
    willDoNothing().given(questionRepository).deleteByQuizId(1L);

    quizService.deleteQuiz(1L, 1L);

    then(questionRepository).should().deleteByQuizId(1L);
    then(quizRepository).should().delete(quiz);
  }

  @Test
  @DisplayName("deleteQuiz_썸네일있음_S3삭제호출")
  void deleteQuiz_withThumbnail_deletesS3Object() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    ReflectionTestUtils.setField(quiz, "thumbnailKey", "thumb.png");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of());
    willDoNothing().given(questionRepository).deleteByQuizId(1L);
    willDoNothing().given(s3Service).deleteQuietly("thumb.png");

    quizService.deleteQuiz(1L, 1L);

    then(s3Service).should().deleteQuietly("thumb.png");
  }

  @Test
  @DisplayName("deleteQuiz_질문imageKey+answerImageKey다름_각각삭제호출")
  void deleteQuiz_questionWithDifferentImageKeys_deletesBoth() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    Question question =
        Question.builder()
            .quiz(quiz)
            .orderNum(1)
            .imageKey("q-image.png")
            .answerImageKey("a-image.png")
            .questionText("문제")
            .answer("정답")
            .build();
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(question));
    willDoNothing().given(questionRepository).deleteByQuizId(1L);
    willDoNothing().given(s3Service).deleteQuietly(anyString());

    quizService.deleteQuiz(1L, 1L);

    then(s3Service).should().deleteQuietly("q-image.png");
    then(s3Service).should().deleteQuietly("a-image.png");
  }

  @Test
  @DisplayName("deleteQuiz_imageKey와answerImageKey동일_deleteQuietly한번만호출")
  void deleteQuiz_questionWithSameImageAndAnswerKey_deletesOnce() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    Question question =
        Question.builder()
            .quiz(quiz)
            .orderNum(1)
            .imageKey("same.png")
            .answerImageKey("same.png")
            .questionText("문제")
            .answer("정답")
            .build();
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(question));
    willDoNothing().given(questionRepository).deleteByQuizId(1L);
    willDoNothing().given(s3Service).deleteQuietly(anyString());

    quizService.deleteQuiz(1L, 1L);

    then(s3Service).should(times(1)).deleteQuietly("same.png");
  }

  @Test
  @DisplayName("deleteQuiz_imageKey없는질문_S3삭제미호출")
  void deleteQuiz_questionWithNoImageKeys_noS3Delete() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    Question question =
        Question.builder()
            .quiz(quiz)
            .orderNum(1)
            .imageKey(null)
            .answerImageKey(null)
            .questionText("텍스트문제")
            .answer("정답")
            .build();
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(question));
    willDoNothing().given(questionRepository).deleteByQuizId(1L);

    quizService.deleteQuiz(1L, 1L);

    then(s3Service).should(never()).deleteQuietly(anyString());
  }

  // ============ deleteAllByUserId / transferOwnershipToAdmin ============

  @Test
  @DisplayName("deleteAllByUserId_본인퀴즈없음_no_op")
  void deleteAllByUserId_noQuizzes_noOp() {
    given(quizRepository.findAllByUserId(1L)).willReturn(List.of());

    quizService.deleteAllByUserId(1L);

    then(questionRepository).should(never()).deleteByQuizIdIn(any());
    then(quizCommentRepository).should(never()).deleteByQuizIdIn(any());
    then(quizAttemptRepository).should(never()).deleteByQuizIdIn(any());
    then(quizStarRepository).should(never()).deleteByQuizIdIn(any());
    then(s3Service).should(never()).deleteQuietly(anyString());
  }

  @Test
  @DisplayName("deleteAllByUserId_본인퀴즈있음_연관일괄삭제_S3정리")
  void deleteAllByUserId_withQuizzes_cascadesAndCleansS3() {
    User user = testUser();
    Quiz quiz1 = testQuiz(user);
    ReflectionTestUtils.setField(quiz1, "id", 10L);
    quiz1.updateThumbnailKey("quiz-images/uuid/thumb1.jpg");
    Quiz quiz2 = testQuiz(user);
    ReflectionTestUtils.setField(quiz2, "id", 11L);
    quiz2.updateThumbnailKey(null);
    given(quizRepository.findAllByUserId(1L)).willReturn(List.of(quiz1, quiz2));

    Question q1 =
        Question.builder()
            .quiz(quiz1)
            .orderNum(1)
            .imageKey("quiz-images/uuid/q1.jpg")
            .answerImageKey("quiz-images/uuid/a1.jpg")
            .questionText(null)
            .answer("정답")
            .build();
    given(questionRepository.findByQuizIdIn(List.of(10L, 11L))).willReturn(List.of(q1));

    quizService.deleteAllByUserId(1L);

    then(quizCommentRepository).should().deleteByQuizIdIn(List.of(10L, 11L));
    then(quizAttemptRepository).should().deleteByQuizIdIn(List.of(10L, 11L));
    then(quizStarRepository).should().deleteByQuizIdIn(List.of(10L, 11L));
    then(questionRepository).should().deleteByQuizIdIn(List.of(10L, 11L));
    then(quizRepository).should().deleteAllInBatch(List.of(quiz1, quiz2));
    then(s3Service).should().deleteQuietly("quiz-images/uuid/thumb1.jpg");
    then(s3Service).should().deleteQuietly("quiz-images/uuid/q1.jpg");
    then(s3Service).should().deleteQuietly("quiz-images/uuid/a1.jpg");
  }

  @Test
  @DisplayName("transferOwnershipToAdmin_repository_위임")
  void transferOwnershipToAdmin_delegates() {
    given(quizRepository.transferOwnership(1L, 999L)).willReturn(3);

    int affected = quizService.transferOwnershipToAdmin(1L, 999L);

    assertThat(affected).isEqualTo(3);
    then(quizRepository).should().transferOwnership(1L, 999L);
  }
}
