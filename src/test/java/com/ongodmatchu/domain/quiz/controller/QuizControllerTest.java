package com.ongodmatchu.domain.quiz.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.quiz.dto.QuestionResponse;
import com.ongodmatchu.domain.quiz.dto.QuizCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuizDetailResponse;
import com.ongodmatchu.domain.quiz.dto.QuizResponse;
import com.ongodmatchu.domain.quiz.dto.QuizShareResponse;
import com.ongodmatchu.domain.quiz.dto.QuizUpdateRequest;
import com.ongodmatchu.domain.quiz.dto.ScoreCountResponse;
import com.ongodmatchu.domain.quiz.dto.ScoreDistributionResponse;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.quiz.service.QuizAttemptService;
import com.ongodmatchu.domain.quiz.service.QuizService;
import com.ongodmatchu.domain.quiz.service.QuizShareService;
import com.ongodmatchu.domain.quiz.service.QuizStarService;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.global.web.AnonIdCookieFilter;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuizController.class)
@AutoConfigureMockMvc(addFilters = false)
class QuizControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private QuizService quizService;
  @MockitoBean private QuizStarService quizStarService;
  @MockitoBean private QuizShareService quizShareService;
  @MockitoBean private QuizAttemptService quizAttemptService;
  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  private User testUser;
  private QuizResponse sampleQuizResponse;

  @BeforeEach
  void setUp() {
    testUser =
        User.builder()
            .email("user@example.com")
            .nickname("퀴즈작성자")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(testUser, "id", 1L);
    ReflectionTestUtils.setField(
        testUser, "publicId", UUID.fromString("00000000-0000-0000-0000-000000000001"));
    ReflectionTestUtils.setField(testUser, "termsVersion", "1.0");
    ReflectionTestUtils.setField(testUser, "privacyVersion", "1.0");

    CustomUserDetails userDetails = new CustomUserDetails(testUser);
    Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);

    sampleQuizResponse =
        new QuizResponse(
            1L,
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            "퀴즈 제목",
            "설명",
            "game",
            null,
            null,
            0,
            0,
            0,
            0,
            false,
            null,
            QuizVisibility.PUBLIC,
            "퀴즈작성자",
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ============ GET /api/quizzes ============

  @Test
  @DisplayName("getQuizList_기본정렬_playCount_DESC_그리고_createdAt_DESC_tiebreaker")
  void getQuizList_defaultSort_playCountDescThenCreatedAtDesc() throws Exception {
    given(quizService.getQuizList(any(), any(), any(), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(sampleQuizResponse)));

    mockMvc.perform(get("/api/quizzes")).andExpect(status().isOk());

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(quizService).should().getQuizList(any(), any(), any(), captor.capture());
    Pageable captured = captor.getValue();
    List<Sort.Order> orders = captured.getSort().toList();
    assertThat(orders).hasSize(2);
    assertThat(orders.get(0).getProperty()).isEqualTo("playCount");
    assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
    assertThat(orders.get(1).getProperty()).isEqualTo("createdAt");
    assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  @DisplayName("getQuizList_최신순_sort_createdAt_desc_전달")
  void getQuizList_latestSort_createdAtDescForwarded() throws Exception {
    given(quizService.getQuizList(any(), any(), any(), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc.perform(get("/api/quizzes").param("sort", "createdAt,desc")).andExpect(status().isOk());

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(quizService).should().getQuizList(any(), any(), any(), captor.capture());
    List<Sort.Order> orders = captor.getValue().getSort().toList();
    assertThat(orders).hasSize(1);
    assertThat(orders.get(0).getProperty()).isEqualTo("createdAt");
    assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  @DisplayName("getQuizList_비로그인_viewerId_null_전달")
  void getQuizList_anonymousViewer_passesNullViewerId() throws Exception {
    SecurityContextHolder.clearContext();
    given(quizService.getQuizList(any(), any(), any(), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc.perform(get("/api/quizzes")).andExpect(status().isOk());

    then(quizService).should().getQuizList(isNull(), isNull(), isNull(), any(Pageable.class));
  }

  @Test
  @DisplayName("getQuizList_인증사용자_viewerId_전달")
  void getQuizList_authenticated_passesViewerId() throws Exception {
    given(quizService.getQuizList(any(), any(), any(), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc.perform(get("/api/quizzes")).andExpect(status().isOk());

    then(quizService).should().getQuizList(isNull(), isNull(), eq(1L), any(Pageable.class));
  }

  @Test
  @DisplayName("getQuizList_category파라미터_그대로_전달")
  void getQuizList_categoryParam_forwarded() throws Exception {
    given(quizService.getQuizList(eq("music"), any(), any(), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc.perform(get("/api/quizzes").param("category", "music")).andExpect(status().isOk());

    then(quizService).should().getQuizList(eq("music"), isNull(), eq(1L), any(Pageable.class));
  }

  @Test
  @DisplayName("getQuizList_q파라미터_서비스로_전달")
  void getQuizList_qParam_forwarded() throws Exception {
    given(quizService.getQuizList(any(), eq("마리오"), any(), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc.perform(get("/api/quizzes").param("q", "마리오")).andExpect(status().isOk());

    then(quizService).should().getQuizList(isNull(), eq("마리오"), eq(1L), any(Pageable.class));
  }

  @Test
  @DisplayName("getQuizList_q미지정_null_전달")
  void getQuizList_qNotProvided_passesNull() throws Exception {
    given(quizService.getQuizList(any(), any(), any(), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc.perform(get("/api/quizzes")).andExpect(status().isOk());

    then(quizService).should().getQuizList(isNull(), isNull(), eq(1L), any(Pageable.class));
  }

  // ============ GET /api/quizzes/{quizId}/score-distribution ============

  @Test
  @DisplayName("getScoreDistribution_비로그인_200반환")
  void getScoreDistribution_anonymous_returns200() throws Exception {
    SecurityContextHolder.clearContext();
    ScoreDistributionResponse response =
        new ScoreDistributionResponse(
            0L, 0.0, List.of(new ScoreCountResponse(0, 0L), new ScoreCountResponse(1, 0L)));
    given(quizAttemptService.getScoreDistribution(eq(1L), isNull())).willReturn(response);

    mockMvc
        .perform(get("/api/quizzes/1/score-distribution"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalAttempts").value(0))
        .andExpect(jsonPath("$.data.averageScore").value(0.0))
        .andExpect(jsonPath("$.data.distribution").isArray());

    then(quizAttemptService).should().getScoreDistribution(eq(1L), isNull());
  }

  @Test
  @DisplayName("getScoreDistribution_로그인_viewerId_전달")
  void getScoreDistribution_authenticated_passesViewerId() throws Exception {
    ScoreDistributionResponse response =
        new ScoreDistributionResponse(0L, 0.0, List.of(new ScoreCountResponse(0, 0L)));
    given(quizAttemptService.getScoreDistribution(eq(1L), eq(1L))).willReturn(response);

    mockMvc.perform(get("/api/quizzes/1/score-distribution")).andExpect(status().isOk());

    then(quizAttemptService).should().getScoreDistribution(eq(1L), eq(1L));
  }

  @Test
  @DisplayName("getScoreDistribution_PRIVATE_외부_404반환")
  void getScoreDistribution_privateExternalViewer_returns404() throws Exception {
    SecurityContextHolder.clearContext();
    given(quizAttemptService.getScoreDistribution(eq(1L), isNull()))
        .willThrow(new BusinessException(ErrorCode.QUIZ_NOT_FOUND));

    mockMvc
        .perform(get("/api/quizzes/1/score-distribution"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("QUIZ_NOT_FOUND"));

    then(quizAttemptService).should().getScoreDistribution(eq(1L), isNull());
  }

  @Test
  @DisplayName("getScoreDistribution_분포_응답_매핑")
  void getScoreDistribution_distributionMapping() throws Exception {
    ScoreDistributionResponse response =
        new ScoreDistributionResponse(
            10L,
            2.3,
            List.of(
                new ScoreCountResponse(0, 1L),
                new ScoreCountResponse(1, 2L),
                new ScoreCountResponse(2, 3L),
                new ScoreCountResponse(3, 4L)));
    given(quizAttemptService.getScoreDistribution(eq(1L), eq(1L))).willReturn(response);

    mockMvc
        .perform(get("/api/quizzes/1/score-distribution"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalAttempts").value(10))
        .andExpect(jsonPath("$.data.averageScore").value(2.3))
        .andExpect(jsonPath("$.data.distribution.length()").value(4))
        .andExpect(jsonPath("$.data.distribution[0].score").value(0))
        .andExpect(jsonPath("$.data.distribution[0].count").value(1))
        .andExpect(jsonPath("$.data.distribution[3].score").value(3))
        .andExpect(jsonPath("$.data.distribution[3].count").value(4));
  }

  // ============ PATCH /api/quizzes/{quizId} ============

  @Test
  @DisplayName("updateQuiz_정상요청_200_QuizResponse반환")
  void updateQuiz_validRequest_returns200WithQuizResponse() throws Exception {
    QuizUpdateRequest request =
        new QuizUpdateRequest("새 제목", "새 설명", "music", null, null, null, null, null);
    QuizResponse updated =
        new QuizResponse(
            1L,
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            "새 제목",
            "새 설명",
            "music",
            null,
            null,
            0,
            0,
            0,
            0,
            false,
            null,
            QuizVisibility.PUBLIC,
            "퀴즈작성자",
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")));
    given(quizService.updateQuiz(eq(1L), eq(1L), any(QuizUpdateRequest.class))).willReturn(updated);

    mockMvc
        .perform(
            patch("/api/quizzes/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.title").value("새 제목"))
        .andExpect(jsonPath("$.data.description").value("새 설명"))
        .andExpect(jsonPath("$.data.category").value("music"));
  }

  @Test
  @DisplayName("updateQuiz_권한없음_403반환")
  void updateQuiz_forbidden_returns403() throws Exception {
    QuizUpdateRequest request =
        new QuizUpdateRequest("새 제목", null, null, null, null, null, null, null);
    given(quizService.updateQuiz(eq(1L), eq(1L), any(QuizUpdateRequest.class)))
        .willThrow(new BusinessException(ErrorCode.QUIZ_FORBIDDEN));

    mockMvc
        .perform(
            patch("/api/quizzes/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("QUIZ_FORBIDDEN"));
  }

  @Test
  @DisplayName("updateQuiz_퀴즈미존재_404반환")
  void updateQuiz_notFound_returns404() throws Exception {
    QuizUpdateRequest request =
        new QuizUpdateRequest("새 제목", null, null, null, null, null, null, null);
    given(quizService.updateQuiz(eq(1L), eq(99L), any(QuizUpdateRequest.class)))
        .willThrow(new BusinessException(ErrorCode.QUIZ_NOT_FOUND));

    mockMvc
        .perform(
            patch("/api/quizzes/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("QUIZ_NOT_FOUND"));
  }

  @Test
  @DisplayName("updateQuiz_title빈문자열_400반환")
  void updateQuiz_blankTitle_returns400() throws Exception {
    String body = "{\"title\":\"\"}";

    mockMvc
        .perform(patch("/api/quizzes/1").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @DisplayName("updateQuiz_questions빈배열_400반환")
  void updateQuiz_emptyQuestions_returns400() throws Exception {
    String body = "{\"questions\":[]}";

    mockMvc
        .perform(patch("/api/quizzes/1").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @DisplayName("updateQuiz_모든필드null_정상_200반환")
  void updateQuiz_allNullFields_returns200() throws Exception {
    QuizUpdateRequest request =
        new QuizUpdateRequest(null, null, null, null, null, null, null, null);
    given(quizService.updateQuiz(eq(1L), eq(1L), any(QuizUpdateRequest.class)))
        .willReturn(sampleQuizResponse);

    mockMvc
        .perform(
            patch("/api/quizzes/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  // ============ DELETE /api/quizzes/{quizId} ============

  @Test
  @DisplayName("deleteQuiz_정상요청_200반환")
  void deleteQuiz_validRequest_returns200() throws Exception {
    willDoNothing().given(quizService).deleteQuiz(1L, 1L);

    mockMvc
        .perform(delete("/api/quizzes/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("deleteQuiz_권한없음_403반환")
  void deleteQuiz_forbidden_returns403() throws Exception {
    willThrow(new BusinessException(ErrorCode.QUIZ_FORBIDDEN))
        .given(quizService)
        .deleteQuiz(1L, 1L);

    mockMvc
        .perform(delete("/api/quizzes/1"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("QUIZ_FORBIDDEN"));
  }

  @Test
  @DisplayName("deleteQuiz_퀴즈미존재_404반환")
  void deleteQuiz_notFound_returns404() throws Exception {
    willThrow(new BusinessException(ErrorCode.QUIZ_NOT_FOUND))
        .given(quizService)
        .deleteQuiz(1L, 99L);

    mockMvc
        .perform(delete("/api/quizzes/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("QUIZ_NOT_FOUND"));
  }

  // ============ POST /api/quizzes/{quizId}/share ============

  @Test
  @DisplayName("share_비로그인_anonId주입_userId_null로_recordShare호출")
  void share_anonymousWithAnonId_callsRecordShareWithNullUserId() throws Exception {
    SecurityContextHolder.clearContext();
    given(quizShareService.recordShare(1L, null, "anon-uuid"))
        .willReturn(new QuizShareResponse(6L, false));

    mockMvc
        .perform(
            post("/api/quizzes/1/share")
                .requestAttr(AnonIdCookieFilter.REQUEST_ATTRIBUTE, "anon-uuid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.shareCount").value(6))
        .andExpect(jsonPath("$.data.alreadyShared").value(false));

    then(quizShareService).should().recordShare(eq(1L), isNull(), eq("anon-uuid"));
  }

  @Test
  @DisplayName("share_로그인_anonId_둘다_전달되고_alreadyShared_true_직렬화")
  void share_authenticatedWithAnonId_passesBothIdsAndSerializesAlreadyShared() throws Exception {
    given(quizShareService.recordShare(1L, 1L, "anon-uuid"))
        .willReturn(new QuizShareResponse(3L, true));

    mockMvc
        .perform(
            post("/api/quizzes/1/share")
                .requestAttr(AnonIdCookieFilter.REQUEST_ATTRIBUTE, "anon-uuid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.shareCount").value(3))
        .andExpect(jsonPath("$.data.alreadyShared").value(true));

    then(quizShareService).should().recordShare(eq(1L), eq(1L), eq("anon-uuid"));
  }

  @Test
  @DisplayName("share_PRIVATE퀴즈_외부viewer_QUIZ_NOT_FOUND_404반환")
  void share_privateQuizExternalViewer_returns404() throws Exception {
    SecurityContextHolder.clearContext();
    given(quizShareService.recordShare(1L, null, "anon-uuid"))
        .willThrow(new BusinessException(ErrorCode.QUIZ_NOT_FOUND));

    mockMvc
        .perform(
            post("/api/quizzes/1/share")
                .requestAttr(AnonIdCookieFilter.REQUEST_ATTRIBUTE, "anon-uuid"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("QUIZ_NOT_FOUND"));

    then(quizShareService).should().recordShare(eq(1L), isNull(), eq("anon-uuid"));
  }

  // ============ 공용 이미지 편집 (원본 + transform) ============

  @Test
  @DisplayName("createQuiz_원본키와_transform_포함_요청_서비스로_캡처_전달")
  void createQuiz_withOriginalAndTransform_capturedByService() throws Exception {
    given(quizService.createQuiz(eq(1L), any(QuizCreateRequest.class)))
        .willReturn(sampleQuizResponse);

    String body =
        """
        {
          "title": "퀴즈 제목",
          "description": "설명",
          "category": "game",
          "thumbnailKey": "thumb-cropped.png",
          "originalThumbnailKey": "thumb-original.png",
          "thumbnailTransform": {"v": 1, "rotate": 90, "flip": true},
          "visibility": "PUBLIC",
          "questions": [
            {
              "imageKey": "q1-cropped.png",
              "originalImageKey": "q1-original.png",
              "imageTransform": {"v": 1, "rotate": 45},
              "answerImageKey": "a1-cropped.png",
              "originalAnswerImageKey": "a1-original.png",
              "answerImageTransform": {"v": 1, "rotate": 180},
              "questionText": "문제1",
              "answer": "정답1"
            }
          ]
        }
        """;

    mockMvc
        .perform(post("/api/quizzes").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    ArgumentCaptor<QuizCreateRequest> captor = ArgumentCaptor.forClass(QuizCreateRequest.class);
    then(quizService).should().createQuiz(eq(1L), captor.capture());
    QuizCreateRequest captured = captor.getValue();
    assertThat(captured.originalThumbnailKey()).isEqualTo("thumb-original.png");
    assertThat(captured.thumbnailTransform()).isNotNull();
    assertThat(captured.thumbnailTransform().get("rotate").asInt()).isEqualTo(90);
    assertThat(captured.thumbnailTransform().get("flip").asBoolean()).isTrue();
    assertThat(captured.questions()).hasSize(1);
    var q = captured.questions().get(0);
    assertThat(q.originalImageKey()).isEqualTo("q1-original.png");
    assertThat(q.imageTransform().get("rotate").asInt()).isEqualTo(45);
    assertThat(q.originalAnswerImageKey()).isEqualTo("a1-original.png");
    assertThat(q.answerImageTransform().get("rotate").asInt()).isEqualTo(180);
  }

  @Test
  @DisplayName("updateQuiz_원본키와_transform_포함_요청_서비스로_캡처_전달")
  void updateQuiz_withOriginalAndTransform_capturedByService() throws Exception {
    given(quizService.updateQuiz(eq(1L), eq(1L), any(QuizUpdateRequest.class)))
        .willReturn(sampleQuizResponse);

    String body =
        """
        {
          "title": "수정 제목",
          "thumbnailKey": "thumb-cropped-v2.png",
          "originalThumbnailKey": "thumb-original-v2.png",
          "thumbnailTransform": {"v": 1, "rotate": 270},
          "questions": [
            {
              "id": 10,
              "imageKey": "q1-cropped-v2.png",
              "originalImageKey": "q1-original-v2.png",
              "imageTransform": {"v": 1, "rotate": 90},
              "answerImageKey": "a1-cropped-v2.png",
              "originalAnswerImageKey": "a1-original-v2.png",
              "answerImageTransform": {"v": 1, "rotate": 30},
              "questionText": "문제1",
              "answer": "정답1"
            }
          ]
        }
        """;

    mockMvc
        .perform(patch("/api/quizzes/1").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    ArgumentCaptor<QuizUpdateRequest> captor = ArgumentCaptor.forClass(QuizUpdateRequest.class);
    then(quizService).should().updateQuiz(eq(1L), eq(1L), captor.capture());
    QuizUpdateRequest captured = captor.getValue();
    assertThat(captured.originalThumbnailKey()).isEqualTo("thumb-original-v2.png");
    assertThat(captured.thumbnailTransform()).isNotNull();
    assertThat(captured.thumbnailTransform().get("rotate").asInt()).isEqualTo(270);
    assertThat(captured.questions()).hasSize(1);
    var q = captured.questions().get(0);
    assertThat(q.id()).isEqualTo(10L);
    assertThat(q.originalImageKey()).isEqualTo("q1-original-v2.png");
    assertThat(q.imageTransform().get("rotate").asInt()).isEqualTo(90);
    assertThat(q.originalAnswerImageKey()).isEqualTo("a1-original-v2.png");
    assertThat(q.answerImageTransform().get("rotate").asInt()).isEqualTo(30);
  }

  @Test
  @DisplayName("getQuizDetail_원본URL과_transform_응답에_중첩객체로_노출")
  void getQuizDetail_exposesOriginalUrlsAndTransform() throws Exception {
    given(quizService.getQuizDetail(eq(1L), eq(1L)))
        .willReturn(
            buildDetailResponse(
                "{\"v\":1,\"rotate\":90}",
                "https://cdn/thumb-original.png",
                "{\"v\":1,\"rotate\":45,\"flip\":true}",
                "https://cdn/q1-original.png",
                "{\"v\":1,\"rotate\":180}",
                "https://cdn/a1-original.png"));

    mockMvc
        .perform(get("/api/quizzes/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.originalThumbnailUrl").value("https://cdn/thumb-original.png"))
        .andExpect(jsonPath("$.data.thumbnailTransform.rotate").value(90))
        .andExpect(
            jsonPath("$.data.questions[0].originalImageUrl").value("https://cdn/q1-original.png"))
        .andExpect(jsonPath("$.data.questions[0].imageTransform.rotate").value(45))
        .andExpect(jsonPath("$.data.questions[0].imageTransform.flip").value(true))
        .andExpect(
            jsonPath("$.data.questions[0].originalAnswerImageUrl")
                .value("https://cdn/a1-original.png"))
        .andExpect(jsonPath("$.data.questions[0].answerImageTransform.rotate").value(180));
  }

  @Test
  @DisplayName("getQuizDetail_transform과_원본_null이면_응답필드_null")
  void getQuizDetail_nullTransformAndOriginal_serializeNull() throws Exception {
    given(quizService.getQuizDetail(eq(1L), eq(1L)))
        .willReturn(buildDetailResponse(null, null, null, null, null, null));

    mockMvc
        .perform(get("/api/quizzes/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.originalThumbnailUrl").doesNotExist())
        .andExpect(jsonPath("$.data.thumbnailTransform").doesNotExist())
        .andExpect(jsonPath("$.data.questions[0].originalImageUrl").doesNotExist())
        .andExpect(jsonPath("$.data.questions[0].imageTransform").doesNotExist())
        .andExpect(jsonPath("$.data.questions[0].originalAnswerImageUrl").doesNotExist())
        .andExpect(jsonPath("$.data.questions[0].answerImageTransform").doesNotExist());
  }

  private QuizDetailResponse buildDetailResponse(
      String thumbnailTransform,
      String originalThumbnailUrl,
      String imageTransform,
      String originalImageUrl,
      String answerImageTransform,
      String originalAnswerImageUrl) {
    Quiz quiz =
        Quiz.builder()
            .user(testUser)
            .title("퀴즈 제목")
            .description("설명")
            .category("game")
            .thumbnailKey("thumb-cropped.png")
            .originalThumbnailKey("thumb-original.png")
            .thumbnailTransform(thumbnailTransform)
            .visibility(QuizVisibility.PUBLIC)
            .build();
    ReflectionTestUtils.setField(quiz, "id", 1L);
    ReflectionTestUtils.setField(
        quiz, "publicId", UUID.fromString("00000000-0000-0000-0000-000000000002"));
    ReflectionTestUtils.setField(quiz, "createdAt", LocalDateTime.of(2024, 1, 1, 0, 0));

    Question question =
        Question.builder()
            .quiz(quiz)
            .orderNum(0)
            .imageKey("q1-cropped.png")
            .originalImageKey("q1-original.png")
            .imageTransform(imageTransform)
            .answerImageKey("a1-cropped.png")
            .originalAnswerImageKey("a1-original.png")
            .answerImageTransform(answerImageTransform)
            .questionText("문제1")
            .answer("정답1")
            .build();
    ReflectionTestUtils.setField(question, "id", 10L);

    QuestionResponse questionResponse =
        QuestionResponse.from(
            question,
            "https://cdn/q1-cropped.png",
            originalImageUrl,
            "https://cdn/a1-cropped.png",
            originalAnswerImageUrl,
            true);

    return QuizDetailResponse.of(
        quiz,
        "https://cdn/thumb-cropped.png",
        originalThumbnailUrl,
        true,
        true,
        80.0,
        "https://cdn/author.png",
        List.of(questionResponse));
  }
}
