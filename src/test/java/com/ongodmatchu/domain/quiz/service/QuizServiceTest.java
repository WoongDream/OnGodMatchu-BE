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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongodmatchu.domain.comment.repository.QuizCommentRepository;
import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.dto.CategoryResponse;
import com.ongodmatchu.domain.quiz.dto.MyQuizListItemResponse;
import com.ongodmatchu.domain.quiz.dto.QuestionCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuestionResponse;
import com.ongodmatchu.domain.quiz.dto.QuestionUpdateRequest;
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

  private static JsonNode transformNode() {
    try {
      return new ObjectMapper()
          .readTree(
              "{\"v\":1,\"rotate\":90,\"flipH\":false,"
                  + "\"crop\":{\"x\":0.1,\"y\":0.1,\"width\":0.5,\"height\":0.6}}");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** 2KB 초과를 유발하는 큰 transform JSON. */
  private static JsonNode oversizedTransformNode() {
    try {
      return new ObjectMapper().readTree("{\"blob\":\"" + "x".repeat(3000) + "\"}");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** 기존 4-arity QuestionCreateRequest 의미 보존 (transform/original 없음). */
  private static QuestionCreateRequest qCreate(
      String imageKey, String answerImageKey, String questionText, String answer) {
    return new QuestionCreateRequest(
        imageKey, null, null, answerImageKey, null, null, questionText, answer);
  }

  /** 기존 5-arity QuestionUpdateRequest 의미 보존 (transform/original 없음). */
  private static QuestionUpdateRequest qUpdate(
      Long id, String imageKey, String answerImageKey, String questionText, String answer) {
    return new QuestionUpdateRequest(
        id, imageKey, null, null, answerImageKey, null, null, questionText, answer);
  }

  /** 기존 6-arity QuizCreateRequest 의미 보존 (thumbnail original/transform 없음). */
  private static QuizCreateRequest quizCreate(
      String title,
      String description,
      String category,
      String thumbnailKey,
      QuizVisibility visibility,
      List<QuestionCreateRequest> questions) {
    return new QuizCreateRequest(
        title, description, category, thumbnailKey, null, null, visibility, questions);
  }

  /** 기존 6-arity QuizUpdateRequest 의미 보존 (thumbnail original/transform 없음). */
  private static QuizUpdateRequest quizUpdate(
      String title,
      String description,
      String category,
      String thumbnailKey,
      QuizVisibility visibility,
      List<QuestionUpdateRequest> questions) {
    return new QuizUpdateRequest(
        title, description, category, thumbnailKey, null, null, visibility, questions);
  }

  @Test
  @DisplayName("카테고리 없이 퀴즈 목록 조회 — PUBLIC 만, 비로그인은 isStarred=null")
  void getQuizList_noCategory_anonymousViewer_isStarredNull() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findByVisibility(eq(QuizVisibility.PUBLIC), any(PageRequest.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));

    var result = quizService.getQuizList(null, null, PageRequest.of(0, 12));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).title()).isEqualTo("퀴즈 제목");
    assertThat(result.getContent().get(0).isStarred()).isNull();
    then(quizRepository)
        .should()
        .findByVisibility(eq(QuizVisibility.PUBLIC), any(PageRequest.class));
    then(quizStarRepository).should(never()).findStarredQuizIds(any(), any());
  }

  @Test
  @DisplayName("카테고리 필터로 퀴즈 목록 조회 — PUBLIC 만, 인증 시 isStarred 채움 (N+1 회피)")
  void getQuizList_withCategory_authenticatedViewer_isStarredFilled() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(
            quizRepository.findByCategoryAndVisibility(
                any(), eq(QuizVisibility.PUBLIC), any(PageRequest.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));
    given(quizStarRepository.findStarredQuizIds(eq(7L), eq(List.of(1L)))).willReturn(List.of(1L));

    var result = quizService.getQuizList("game", 7L, PageRequest.of(0, 12));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).category()).isEqualTo("game");
    assertThat(result.getContent().get(0).isStarred()).isTrue();
    then(quizRepository)
        .should()
        .findByCategoryAndVisibility(eq("game"), eq(QuizVisibility.PUBLIC), any(PageRequest.class));
    then(quizStarRepository).should(times(1)).findStarredQuizIds(eq(7L), eq(List.of(1L)));
  }

  @Test
  @DisplayName("퀴즈 목록 — 인증 사용자가 스타 안 누른 경우 isStarred=false")
  void getQuizList_authenticatedViewer_notStarred_isFalse() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findByVisibility(eq(QuizVisibility.PUBLIC), any(PageRequest.class)))
        .willReturn(new PageImpl<>(List.of(quiz)));
    given(quizStarRepository.findStarredQuizIds(eq(7L), eq(List.of(1L)))).willReturn(List.of());

    var result = quizService.getQuizList(null, 7L, PageRequest.of(0, 12));

    assertThat(result.getContent().get(0).isStarred()).isFalse();
  }

  @Test
  @DisplayName("퀴즈 목록 — 빈 페이지면 findStarredQuizIds 호출 없음")
  void getQuizList_emptyPage_skipsStarLookup() {
    given(quizRepository.findByVisibility(eq(QuizVisibility.PUBLIC), any(PageRequest.class)))
        .willReturn(new PageImpl<>(List.of()));

    var result = quizService.getQuizList(null, 7L, PageRequest.of(0, 12));

    assertThat(result.getContent()).isEmpty();
    then(quizStarRepository).should(never()).findStarredQuizIds(any(), any());
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
        quizCreate("기본비공개", null, "etc", null, null, List.of(qCreate(null, null, "문제1", "정답1")));

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
        quizCreate(
            "공개퀴즈",
            null,
            "etc",
            null,
            QuizVisibility.PUBLIC,
            List.of(qCreate(null, null, "문제1", "정답1")));

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
        quizCreate("새 퀴즈", "설명", "music", null, null, List.of(qCreate(null, null, "문제1", "정답1")));

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
        quizCreate(
            "이미지 퀴즈",
            "설명",
            "entertainment",
            "quiz-images/uid/thumb.png",
            null,
            List.of(qCreate("quiz-images/uid/q.png", "quiz-images/uid/a.png", "문제1", "정답1")));

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
        quizCreate("동일 key 퀴즈", null, "etc", null, null, List.of(qCreate(key, key, "문제1", "정답1")));

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
        quizCreate("잘못된 카테고리", "설명", "역사", null, null, List.of(qCreate(null, null, "문제1", "정답1")));

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
        quizCreate("새 퀴즈", "설명", "music", null, null, List.of(qCreate(null, null, "문제1", "정답1")));

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
        quizCreate(
            "멀티 퀴즈",
            "설명",
            "music",
            null,
            null,
            List.of(
                qCreate(null, null, "문제1", "정답1"),
                qCreate(null, null, "문제2", "정답2"),
                qCreate(null, null, "문제3", "정답3")));

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
            () -> quizService.updateQuiz(1L, 99L, quizUpdate(null, null, null, null, null, null)))
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
            () -> quizService.updateQuiz(999L, 1L, quizUpdate(null, null, null, null, null, null)))
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
        quizService.updateQuiz(1L, 1L, quizUpdate("새제목", null, null, null, null, null));

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
        quizService.updateQuiz(1L, 1L, quizUpdate(null, "새설명", null, null, null, null));

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
        quizService.updateQuiz(1L, 1L, quizUpdate(null, null, "music", null, null, null));

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
            () -> quizService.updateQuiz(1L, 1L, quizUpdate(null, null, "역사", null, null, null)))
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

    quizService.updateQuiz(1L, 1L, quizUpdate(null, null, null, "new-key.png", null, null));

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

    quizService.updateQuiz(1L, 1L, quizUpdate(null, null, null, "new-key.png", null, null));

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

    quizService.updateQuiz(1L, 1L, quizUpdate(null, null, null, "same-key.png", null, null));

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
            1L, 1L, quizUpdate(null, null, null, null, QuizVisibility.PUBLIC, null));

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
            1L, 1L, quizUpdate(null, null, null, null, QuizVisibility.PRIVATE, null));

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
        quizService.updateQuiz(1L, 1L, quizUpdate(null, null, null, null, null, null));

    assertThat(result.title()).isEqualTo("퀴즈 제목");
    assertThat(result.category()).isEqualTo("game");
    then(s3Service).should(never()).verifyKeyOwnedAndCompleted(anyLong(), anyString());
    then(s3Service).should(never()).deleteQuietly(anyString());
  }

  // ============ updateQuiz — questions diff ============

  private Question testQuestion(Quiz quiz, long id, int orderNum, String imageKey, String text) {
    Question q =
        Question.builder()
            .quiz(quiz)
            .orderNum(orderNum)
            .imageKey(imageKey)
            .answerImageKey(null)
            .questionText(text)
            .answer("ans" + id)
            .build();
    ReflectionTestUtils.setField(q, "id", id);
    return q;
  }

  @Test
  @DisplayName("updateQuiz_questions_null이면_questions미수정")
  void updateQuiz_questionsNull_skipsDiff() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    quizService.updateQuiz(1L, 1L, quizUpdate(null, null, null, null, null, null));

    then(questionRepository).should(never()).findByQuizIdOrderByOrderNum(anyLong());
    then(questionRepository).should(never()).deleteAll(any());
    then(questionRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("updateQuiz_questions_기존id갱신만")
  void updateQuiz_questions_updateExistingOnly() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    Question existing = testQuestion(quiz, 1L, 1, null, "원래문제");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(existing));

    LocalDateTime before = LocalDateTime.now().minusSeconds(1);
    ReflectionTestUtils.setField(quiz, "updatedAt", before);

    QuizUpdateRequest request =
        quizUpdate(null, null, null, null, null, List.of(qUpdate(1L, null, null, "수정문제", "수정정답")));

    quizService.updateQuiz(1L, 1L, request);

    assertThat(existing.getQuestionText()).isEqualTo("수정문제");
    assertThat(existing.getAnswer()).isEqualTo("수정정답");
    assertThat(existing.getOrderNum()).isEqualTo(1);
    then(questionRepository).should(never()).save(any());
    then(questionRepository).should(never()).deleteAll(any());
    assertThat(quiz.getUpdatedAt()).isAfterOrEqualTo(before);
  }

  @Test
  @DisplayName("updateQuiz_questions_신규추가")
  void updateQuiz_questions_insertsNew() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    Question existing = testQuestion(quiz, 1L, 1, null, "기존문제");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(existing));
    given(questionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    QuizUpdateRequest request =
        quizUpdate(
            null,
            null,
            null,
            null,
            null,
            List.of(
                qUpdate(1L, null, null, "기존수정", "정답1"), qUpdate(null, null, null, "신규문제", "정답2")));

    quizService.updateQuiz(1L, 1L, request);

    assertThat(existing.getOrderNum()).isEqualTo(1);
    assertThat(existing.getQuestionText()).isEqualTo("기존수정");

    ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
    then(questionRepository).should(times(1)).save(captor.capture());
    Question saved = captor.getValue();
    assertThat(saved.getOrderNum()).isEqualTo(2);
    assertThat(saved.getQuestionText()).isEqualTo("신규문제");
    assertThat(saved.getAnswer()).isEqualTo("정답2");
  }

  @Test
  @DisplayName("updateQuiz_questions_삭제")
  void updateQuiz_questions_deletesMissing() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    Question q1 = testQuestion(quiz, 1L, 1, null, "q1");
    Question q2 = testQuestion(quiz, 2L, 2, "q2.png", "q2");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q1, q2));

    QuizUpdateRequest request =
        quizUpdate(null, null, null, null, null, List.of(qUpdate(1L, null, null, "q1", "ans1")));

    quizService.updateQuiz(1L, 1L, request);

    ArgumentCaptor<List<Question>> captor = ArgumentCaptor.forClass(List.class);
    then(questionRepository).should().deleteAll(captor.capture());
    assertThat(captor.getValue()).containsExactly(q2);
    then(s3Service).should().deleteQuietly("q2.png");
  }

  @Test
  @DisplayName("updateQuiz_questions_imageKey변경시_새키verify_옛키deleteQuietly")
  void updateQuiz_questions_imageKeyChanged_verifiesNewAndDeletesOld() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    Question existing = testQuestion(quiz, 1L, 1, "old.png", "q");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(existing));

    QuizUpdateRequest request =
        quizUpdate(
            null, null, null, null, null, List.of(qUpdate(1L, "new.png", null, "q", "ans1")));

    quizService.updateQuiz(1L, 1L, request);

    then(s3Service).should().verifyKeyOwnedAndCompleted(1L, "new.png");
    then(s3Service).should().deleteQuietly("old.png");
    assertThat(existing.getImageKey()).isEqualTo("new.png");
  }

  @Test
  @DisplayName("updateQuiz_questions_imageKey동일시_verify와delete둘다호출안됨")
  void updateQuiz_questions_imageKeyUnchanged_noVerifyNoDelete() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    Question existing = testQuestion(quiz, 1L, 1, "same.png", "q");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(existing));

    QuizUpdateRequest request =
        quizUpdate(
            null, null, null, null, null, List.of(qUpdate(1L, "same.png", null, "q", "ans1")));

    quizService.updateQuiz(1L, 1L, request);

    then(s3Service).should(never()).verifyKeyOwnedAndCompleted(anyLong(), anyString());
    then(s3Service).should(never()).deleteQuietly(anyString());
  }

  @Test
  @DisplayName("updateQuiz_questions_answerImageKey가imageKey와같으면_verify한번만")
  void updateQuiz_questions_answerImageKeySameAsImageKey_verifiesOnce() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    Question existing = testQuestion(quiz, 1L, 1, null, "q");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(existing));

    String key = "shared.png";
    QuizUpdateRequest request =
        quizUpdate(null, null, null, null, null, List.of(qUpdate(1L, key, key, "q", "ans1")));

    quizService.updateQuiz(1L, 1L, request);

    then(s3Service).should(times(1)).verifyKeyOwnedAndCompleted(1L, key);
  }

  @Test
  @DisplayName("updateQuiz_questions_삭제된question의키가다른question에서재사용되면_deleteQuietly안함")
  void updateQuiz_questions_keyReusedAfterDelete_noDelete() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    Question q1 = testQuestion(quiz, 1L, 1, "shared.png", "q1");
    Question q2 = testQuestion(quiz, 2L, 2, "shared.png", "q2");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q1, q2));

    QuizUpdateRequest request =
        quizUpdate(
            null, null, null, null, null, List.of(qUpdate(1L, "shared.png", null, "q1", "ans1")));

    quizService.updateQuiz(1L, 1L, request);

    then(s3Service).should(never()).deleteQuietly("shared.png");
  }

  @Test
  @DisplayName("updateQuiz_questions_orderNum_payload순서대로_재할당")
  void updateQuiz_questions_orderNumReassignedByPayloadOrder() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    Question q1 = testQuestion(quiz, 1L, 1, null, "q1");
    Question q2 = testQuestion(quiz, 2L, 2, null, "q2");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q1, q2));

    QuizUpdateRequest request =
        quizUpdate(
            null,
            null,
            null,
            null,
            null,
            List.of(qUpdate(2L, null, null, "q2", "ans2"), qUpdate(1L, null, null, "q1", "ans1")));

    quizService.updateQuiz(1L, 1L, request);

    assertThat(q2.getOrderNum()).isEqualTo(1);
    assertThat(q1.getOrderNum()).isEqualTo(2);
  }

  @Test
  @DisplayName("updateQuiz_questions_카운터보존_회귀가드")
  void updateQuiz_questions_preservesCounters() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    ReflectionTestUtils.setField(quiz, "playCount", 10);
    ReflectionTestUtils.setField(quiz, "starCount", 10);
    ReflectionTestUtils.setField(quiz, "commentCount", 10);
    ReflectionTestUtils.setField(quiz, "shareCount", 10);
    Question existing = testQuestion(quiz, 1L, 1, null, "q");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(existing));

    QuizUpdateRequest request =
        quizUpdate(null, null, null, null, null, List.of(qUpdate(1L, null, null, "수정q", "ans1")));

    quizService.updateQuiz(1L, 1L, request);

    assertThat(quiz.getPlayCount()).isEqualTo(10);
    assertThat(quiz.getStarCount()).isEqualTo(10);
    assertThat(quiz.getCommentCount()).isEqualTo(10);
    assertThat(quiz.getShareCount()).isEqualTo(10);
  }

  @Test
  @DisplayName("updateQuiz_questions에_quiz소속이아닌id섞임_QUESTION_NOT_FOUND예외")
  void updateQuiz_questions_unknownId_throwsQuestionNotFound() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    Question q1 = testQuestion(quiz, 1L, 1, null, "q1");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q1));

    QuizUpdateRequest request =
        quizUpdate(null, null, null, null, null, List.of(qUpdate(999L, null, null, "x", "y")));

    assertThatThrownBy(() -> quizService.updateQuiz(1L, 1L, request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUESTION_NOT_FOUND);

    then(questionRepository).should(never()).deleteAll(any());
    then(questionRepository).should(never()).save(any());
    then(s3Service).should(never()).verifyKeyOwnedAndCompleted(anyLong(), anyString());
    then(s3Service).should(never()).deleteQuietly(anyString());
  }

  @Test
  @DisplayName("updateQuiz_메타와questions동시변경")
  void updateQuiz_metaAndQuestions_bothApplied() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    Question existing = testQuestion(quiz, 1L, 1, null, "q");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(existing));

    QuizUpdateRequest request =
        quizUpdate("새제목", null, null, null, null, List.of(qUpdate(1L, null, null, "수정q", "수정ans")));

    QuizResponse result = quizService.updateQuiz(1L, 1L, request);

    assertThat(result.title()).isEqualTo("새제목");
    assertThat(quiz.getTitle()).isEqualTo("새제목");
    assertThat(existing.getQuestionText()).isEqualTo("수정q");
    assertThat(existing.getAnswer()).isEqualTo("수정ans");
  }

  // ============ 공용 이미지 편집: 원본 보존 + transform 메타 ============

  @Test
  @DisplayName("createQuiz_원본키+transform_썸네일/문제/정답_모두_저장")
  void createQuiz_persistsOriginalKeysAndTransforms() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(quizRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    given(questionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    JsonNode node = transformNode();
    QuizCreateRequest request =
        new QuizCreateRequest(
            "편집 퀴즈",
            "설명",
            "music",
            "crop/thumb.png",
            "orig/thumb.png",
            node,
            QuizVisibility.PUBLIC,
            List.of(
                new QuestionCreateRequest(
                    "crop/q.png",
                    "orig/q.png",
                    node,
                    "crop/a.png",
                    "orig/a.png",
                    node,
                    "문제1",
                    "정답1")));

    quizService.createQuiz(1L, request);

    ArgumentCaptor<Quiz> quizCaptor = ArgumentCaptor.forClass(Quiz.class);
    then(quizRepository).should().save(quizCaptor.capture());
    Quiz savedQuiz = quizCaptor.getValue();
    assertThat(savedQuiz.getThumbnailKey()).isEqualTo("crop/thumb.png");
    assertThat(savedQuiz.getOriginalThumbnailKey()).isEqualTo("orig/thumb.png");
    assertThat(savedQuiz.getThumbnailTransform()).isEqualTo(node.toString());

    ArgumentCaptor<Question> qCaptor = ArgumentCaptor.forClass(Question.class);
    then(questionRepository).should().save(qCaptor.capture());
    Question savedQ = qCaptor.getValue();
    assertThat(savedQ.getImageKey()).isEqualTo("crop/q.png");
    assertThat(savedQ.getOriginalImageKey()).isEqualTo("orig/q.png");
    assertThat(savedQ.getImageTransform()).isEqualTo(node.toString());
    assertThat(savedQ.getAnswerImageKey()).isEqualTo("crop/a.png");
    assertThat(savedQ.getOriginalAnswerImageKey()).isEqualTo("orig/a.png");
    assertThat(savedQ.getAnswerImageTransform()).isEqualTo(node.toString());
  }

  @Test
  @DisplayName("createQuiz_원본키가_크롭키와다르면_원본키도_verify호출")
  void createQuiz_verifiesOriginalKeysWhenCropped() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(quizRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    given(questionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    QuizCreateRequest request =
        new QuizCreateRequest(
            "크롭 퀴즈",
            null,
            "music",
            "crop/thumb.png",
            "orig/thumb.png",
            null,
            QuizVisibility.PUBLIC,
            List.of(
                new QuestionCreateRequest(
                    "crop/q.png",
                    "orig/q.png",
                    null,
                    "crop/a.png",
                    "orig/a.png",
                    null,
                    "문제1",
                    "정답1")));

    quizService.createQuiz(1L, request);

    then(s3Service).should().verifyKeyOwnedAndCompleted(1L, "crop/thumb.png");
    then(s3Service).should().verifyKeyOwnedAndCompleted(1L, "orig/thumb.png");
    then(s3Service).should().verifyKeyOwnedAndCompleted(1L, "crop/q.png");
    then(s3Service).should().verifyKeyOwnedAndCompleted(1L, "orig/q.png");
    then(s3Service).should().verifyKeyOwnedAndCompleted(1L, "crop/a.png");
    then(s3Service).should().verifyKeyOwnedAndCompleted(1L, "orig/a.png");
  }

  @Test
  @DisplayName("createQuiz_원본키가_크롭키와같으면_원본키_추가verify안함")
  void createQuiz_noExtraVerifyWhenOriginalEqualsCropped() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(quizRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    given(questionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    QuizCreateRequest request =
        new QuizCreateRequest(
            "무크롭 퀴즈",
            null,
            "music",
            "thumb.png",
            "thumb.png",
            null,
            QuizVisibility.PUBLIC,
            List.of(
                new QuestionCreateRequest(
                    "q.png", "q.png", null, "a.png", "a.png", null, "문제1", "정답1")));

    quizService.createQuiz(1L, request);

    then(s3Service).should(times(1)).verifyKeyOwnedAndCompleted(1L, "thumb.png");
    then(s3Service).should(times(1)).verifyKeyOwnedAndCompleted(1L, "q.png");
    then(s3Service).should(times(1)).verifyKeyOwnedAndCompleted(1L, "a.png");
  }

  @Test
  @DisplayName("createQuiz_transform_2KB초과_INVALID_INPUT_save미발생")
  void createQuiz_oversizedTransform_throwsAndDoesNotSave() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    QuizCreateRequest request =
        new QuizCreateRequest(
            "큰 transform",
            null,
            "music",
            "thumb.png",
            null,
            oversizedTransformNode(),
            QuizVisibility.PUBLIC,
            List.of(qCreate(null, null, "문제1", "정답1")));

    assertThatThrownBy(() -> quizService.createQuiz(1L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_INPUT);

    then(quizRepository).should(never()).save(any());
    then(questionRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("updateQuiz_썸네일교체_새원본+transform적용_이전키들_deleteQuietly")
  void updateQuiz_replaceThumbnail_appliesAndDeletesPreviousKeys() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    ReflectionTestUtils.setField(quiz, "thumbnailKey", "old/crop.png");
    ReflectionTestUtils.setField(quiz, "originalThumbnailKey", "old/orig.png");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    JsonNode node = transformNode();
    QuizUpdateRequest request =
        new QuizUpdateRequest(null, null, null, "new/crop.png", "new/orig.png", node, null, null);

    quizService.updateQuiz(1L, 1L, request);

    assertThat(quiz.getThumbnailKey()).isEqualTo("new/crop.png");
    assertThat(quiz.getOriginalThumbnailKey()).isEqualTo("new/orig.png");
    assertThat(quiz.getThumbnailTransform()).isEqualTo(node.toString());
    then(s3Service).should().verifyKeyOwnedAndCompleted(1L, "new/crop.png");
    then(s3Service).should().verifyKeyOwnedAndCompleted(1L, "new/orig.png");
    then(s3Service).should().deleteQuietly("old/crop.png");
    then(s3Service).should().deleteQuietly("old/orig.png");
  }

  @Test
  @DisplayName("updateQuiz_썸네일교체_이전원본이_새키와동일하면_그키는_삭제안함")
  void updateQuiz_replaceThumbnail_reusedOriginalNotDeleted() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    ReflectionTestUtils.setField(quiz, "thumbnailKey", "old/crop.png");
    ReflectionTestUtils.setField(quiz, "originalThumbnailKey", "shared/orig.png");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    QuizUpdateRequest request =
        new QuizUpdateRequest(
            null, null, null, "new/crop.png", "shared/orig.png", null, null, null);

    quizService.updateQuiz(1L, 1L, request);

    then(s3Service).should().deleteQuietly("old/crop.png");
    then(s3Service).should(never()).deleteQuietly("shared/orig.png");
  }

  @Test
  @DisplayName("applyQuestionsDiff_이미지교체_옛크롭+옛원본_deleteQuietly_새transform반영")
  void updateQuiz_questions_imageReplaced_deletesOldCropAndOriginal() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    Question existing = testQuestion(quiz, 1L, 1, "old/crop.png", "q");
    ReflectionTestUtils.setField(existing, "originalImageKey", "old/orig.png");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(existing));

    JsonNode node = transformNode();
    QuizUpdateRequest request =
        new QuizUpdateRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(
                new QuestionUpdateRequest(
                    1L, "new/crop.png", "new/orig.png", node, null, null, null, "q", "ans")));

    quizService.updateQuiz(1L, 1L, request);

    assertThat(existing.getImageKey()).isEqualTo("new/crop.png");
    assertThat(existing.getOriginalImageKey()).isEqualTo("new/orig.png");
    assertThat(existing.getImageTransform()).isEqualTo(node.toString());
    then(s3Service).should().deleteQuietly("old/crop.png");
    then(s3Service).should().deleteQuietly("old/orig.png");
  }

  @Test
  @DisplayName("applyQuestionsDiff_질문삭제시_원본키도_deleteQuietly")
  void updateQuiz_questions_deleted_removesOriginalKeys() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    Question q1 = testQuestion(quiz, 1L, 1, null, "q1");
    Question q2 = testQuestion(quiz, 2L, 2, "q2/crop.png", "q2");
    ReflectionTestUtils.setField(q2, "originalImageKey", "q2/orig.png");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q1, q2));

    QuizUpdateRequest request =
        new QuizUpdateRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(qUpdate(1L, null, null, "q1", "ans1")));

    quizService.updateQuiz(1L, 1L, request);

    then(s3Service).should().deleteQuietly("q2/crop.png");
    then(s3Service).should().deleteQuietly("q2/orig.png");
  }

  @Test
  @DisplayName("applyQuestionsDiff_삭제질문의원본키가_다른질문에서재사용_삭제안함")
  void updateQuiz_questions_reusedOriginalKeyNotDeleted() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    Question q1 = testQuestion(quiz, 1L, 1, "q1/crop.png", "q1");
    ReflectionTestUtils.setField(q1, "originalImageKey", "shared/orig.png");
    Question q2 = testQuestion(quiz, 2L, 2, "q2/crop.png", "q2");
    ReflectionTestUtils.setField(q2, "originalImageKey", "shared/orig.png");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q1, q2));

    // q2 삭제, q1 유지 (q1 이 shared/orig.png 를 계속 참조)
    QuizUpdateRequest request =
        new QuizUpdateRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(
                new QuestionUpdateRequest(
                    1L, "q1/crop.png", "shared/orig.png", null, null, null, null, "q1", "ans1")));

    quizService.updateQuiz(1L, 1L, request);

    then(s3Service).should().deleteQuietly("q2/crop.png");
    then(s3Service).should(never()).deleteQuietly("shared/orig.png");
  }

  @Test
  @DisplayName("applyQuestionsDiff_변경없는_원본키는_보존")
  void updateQuiz_questions_unchangedOriginalKeyPreserved() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    Question existing = testQuestion(quiz, 1L, 1, "crop.png", "q");
    ReflectionTestUtils.setField(existing, "originalImageKey", "orig.png");
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(existing));

    QuizUpdateRequest request =
        new QuizUpdateRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(
                new QuestionUpdateRequest(
                    1L, "crop.png", "orig.png", null, null, null, null, "q", "ans1")));

    quizService.updateQuiz(1L, 1L, request);

    then(s3Service).should(never()).deleteQuietly(anyString());
  }

  @Test
  @DisplayName("getQuizDetail_응답매핑_원본URL+transform_썸네일/문제/정답_모두채움")
  void getQuizDetail_mapsOriginalUrlsAndTransforms() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    JsonNode node = transformNode();
    ReflectionTestUtils.setField(quiz, "thumbnailKey", "crop/thumb.png");
    ReflectionTestUtils.setField(quiz, "originalThumbnailKey", "orig/thumb.png");
    ReflectionTestUtils.setField(quiz, "thumbnailTransform", node.toString());

    Question question = testQuestion(quiz, 1L, 1, "crop/q.png", "q");
    ReflectionTestUtils.setField(question, "originalImageKey", "orig/q.png");
    ReflectionTestUtils.setField(question, "imageTransform", node.toString());
    ReflectionTestUtils.setField(question, "answerImageKey", "crop/a.png");
    ReflectionTestUtils.setField(question, "originalAnswerImageKey", "orig/a.png");
    ReflectionTestUtils.setField(question, "answerImageTransform", node.toString());

    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(question));
    given(s3Service.batchPresignViewUrls(any()))
        .willReturn(
            Map.of(
                "crop/thumb.png", "u-crop-thumb",
                "orig/thumb.png", "u-orig-thumb",
                "crop/q.png", "u-crop-q",
                "orig/q.png", "u-orig-q",
                "crop/a.png", "u-crop-a",
                "orig/a.png", "u-orig-a"));

    // 원본/transform 은 소유자(편집)에게만 노출 → viewer = 소유자(id 1L).
    QuizDetailResponse result = quizService.getQuizDetail(1L, 1L);

    assertThat(result.thumbnailUrl()).isEqualTo("u-crop-thumb");
    assertThat(result.originalThumbnailUrl()).isEqualTo("u-orig-thumb");
    assertThat(result.thumbnailTransform()).isEqualTo(node.toString());

    QuestionResponse qr = result.questions().get(0);
    assertThat(qr.imageUrl()).isEqualTo("u-crop-q");
    assertThat(qr.originalImageUrl()).isEqualTo("u-orig-q");
    assertThat(qr.imageTransform()).isEqualTo(node.toString());
    assertThat(qr.answerImageUrl()).isEqualTo("u-crop-a");
    assertThat(qr.originalAnswerImageUrl()).isEqualTo("u-orig-a");
    assertThat(qr.answerImageTransform()).isEqualTo(node.toString());
  }

  @Test
  @DisplayName("getQuizDetail_레거시행_원본키null+transformNull_원본URL과transform모두null")
  void getQuizDetail_legacyRow_nullOriginalAndTransform() {
    User user = testUser();
    Quiz quiz = testQuiz(user);
    ReflectionTestUtils.setField(quiz, "thumbnailKey", "crop/thumb.png");
    // originalThumbnailKey 와 thumbnailTransform 은 null (레거시)
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of());
    given(s3Service.batchPresignViewUrls(any()))
        .willReturn(Map.of("crop/thumb.png", "u-crop-thumb"));

    QuizDetailResponse result = quizService.getQuizDetail(1L, null);

    assertThat(result.thumbnailUrl()).isEqualTo("u-crop-thumb");
    assertThat(result.originalThumbnailUrl()).isNull();
    assertThat(result.thumbnailTransform()).isNull();
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
