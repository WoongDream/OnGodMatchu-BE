package com.ongodmatchu.domain.quiz.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.quiz.dto.AttemptAnswerRequest;
import com.ongodmatchu.domain.quiz.dto.AttemptCreateRequest;
import com.ongodmatchu.domain.quiz.dto.AttemptItemResultResponse;
import com.ongodmatchu.domain.quiz.dto.AttemptResultResponse;
import com.ongodmatchu.domain.quiz.service.QuizAttemptService;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuizAttemptController.class)
@AutoConfigureMockMvc(addFilters = false)
class QuizAttemptControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private QuizAttemptService quizAttemptService;
  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  private User testUser;

  @BeforeEach
  void setUp() {
    testUser =
        User.builder()
            .email("user@example.com")
            .nickname("퀴즈풀이어")
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
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ─── POST /api/quizzes/{quizId}/attempts ───────────────────────────────────

  @Test
  @DisplayName("submit_로그인_정상요청_201_AttemptResultResponse반환")
  void submit_authenticated_validRequest_returns201WithResponse() throws Exception {
    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "정답")), 10);

    AttemptResultResponse response =
        new AttemptResultResponse(
            100L,
            1,
            1,
            100.0,
            null,
            List.of(new AttemptItemResultResponse(10L, true, "정답", "정답", null)));

    given(quizAttemptService.submit(eq(1L), eq(1L), any(AttemptCreateRequest.class)))
        .willReturn(response);

    mockMvc
        .perform(
            post("/api/quizzes/1/attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.attemptId").value(100))
        .andExpect(jsonPath("$.data.score").value(1))
        .andExpect(jsonPath("$.data.totalQuestions").value(1))
        .andExpect(jsonPath("$.data.percent").value(100.0))
        .andExpect(jsonPath("$.data.topPercentile").isEmpty())
        .andExpect(jsonPath("$.data.results").isArray())
        .andExpect(jsonPath("$.data.results[0].correct").value(true))
        .andExpect(jsonPath("$.data.results[0].correctAnswer").value("정답"));
  }

  @Test
  @DisplayName("submit_비로그인_201_attemptId_null반환")
  void submit_anonymous_returns201WithNullAttemptId() throws Exception {
    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "정답")), null);

    AttemptResultResponse response =
        new AttemptResultResponse(
            null,
            1,
            1,
            100.0,
            null,
            List.of(new AttemptItemResultResponse(10L, true, "정답", "정답", null)));

    SecurityContextHolder.clearContext();

    given(quizAttemptService.submit(eq(1L), isNull(), any(AttemptCreateRequest.class)))
        .willReturn(response);

    mockMvc
        .perform(
            post("/api/quizzes/1/attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.attemptId").isEmpty());
  }

  @Test
  @DisplayName("submit_answers빈배열_400반환")
  void submit_emptyAnswers_returns400() throws Exception {
    String body = "{\"answers\":[]}";

    mockMvc
        .perform(
            post("/api/quizzes/1/attempts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @DisplayName("submit_answers필드없음_400반환")
  void submit_missingAnswers_returns400() throws Exception {
    String body = "{}";

    mockMvc
        .perform(
            post("/api/quizzes/1/attempts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @DisplayName("submit_timeLimitSec포함_201수신")
  void submit_withTimeLimitSec_returns201() throws Exception {
    String body = "{\"answers\":[{\"questionId\":10,\"userAnswer\":\"정답\"}],\"timeLimitSec\":10}";

    AttemptResultResponse response =
        new AttemptResultResponse(
            100L,
            1,
            1,
            100.0,
            null,
            List.of(new AttemptItemResultResponse(10L, true, "정답", "정답", null)));
    given(quizAttemptService.submit(eq(1L), eq(1L), any(AttemptCreateRequest.class)))
        .willReturn(response);

    mockMvc
        .perform(
            post("/api/quizzes/1/attempts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.attemptId").value(100));
  }

  @Test
  @DisplayName("submit_timeLimitSec_0이하_400반환")
  void submit_nonPositiveTimeLimitSec_returns400() throws Exception {
    String body = "{\"answers\":[{\"questionId\":10,\"userAnswer\":\"정답\"}],\"timeLimitSec\":0}";

    mockMvc
        .perform(
            post("/api/quizzes/1/attempts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @DisplayName("submit_questionId_null_400반환")
  void submit_nullQuestionId_returns400() throws Exception {
    String body = "{\"answers\":[{\"questionId\":null,\"userAnswer\":\"답\"}]}";

    mockMvc
        .perform(
            post("/api/quizzes/1/attempts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @DisplayName("submit_userAnswer빈값_201수신_시간초과_빈답_허용")
  void submit_blankUserAnswer_returns201() throws Exception {
    String body = "{\"answers\":[{\"questionId\":1,\"userAnswer\":\"\"}]}";

    AttemptResultResponse response =
        new AttemptResultResponse(
            200L,
            0,
            1,
            0.0,
            null,
            List.of(new AttemptItemResultResponse(1L, false, "정답", "", null)));
    given(quizAttemptService.submit(eq(1L), eq(1L), any(AttemptCreateRequest.class)))
        .willReturn(response);

    mockMvc
        .perform(
            post("/api/quizzes/1/attempts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.results[0].correct").value(false));
  }

  @Test
  @DisplayName("submit_userAnswer_null값_400반환")
  void submit_nullUserAnswer_returns400() throws Exception {
    String body = "{\"answers\":[{\"questionId\":1,\"userAnswer\":null}]}";

    mockMvc
        .perform(
            post("/api/quizzes/1/attempts").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @DisplayName("submit_PRIVATE퀴즈_외부뷰어_404반환")
  void submit_privateQuiz_externalViewer_returns404() throws Exception {
    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "답")), null);

    given(quizAttemptService.submit(eq(1L), eq(1L), any(AttemptCreateRequest.class)))
        .willThrow(new BusinessException(ErrorCode.QUIZ_NOT_FOUND));

    mockMvc
        .perform(
            post("/api/quizzes/1/attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("QUIZ_NOT_FOUND"));
  }

  @Test
  @DisplayName("submit_잘못된questionId_404반환")
  void submit_invalidQuestionId_returns404() throws Exception {
    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(999L, "답")), null);

    given(quizAttemptService.submit(eq(1L), eq(1L), any(AttemptCreateRequest.class)))
        .willThrow(new BusinessException(ErrorCode.QUESTION_NOT_FOUND));

    mockMvc
        .perform(
            post("/api/quizzes/1/attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("QUESTION_NOT_FOUND"));
  }

  @Test
  @DisplayName("submit_응시자_2명이상_topPercentile_숫자응답")
  void submit_multipleAttempts_topPercentileSerialized() throws Exception {
    AttemptCreateRequest request =
        new AttemptCreateRequest(List.of(new AttemptAnswerRequest(10L, "정답")), null);

    AttemptResultResponse response =
        new AttemptResultResponse(
            101L,
            1,
            1,
            100.0,
            5.0,
            List.of(new AttemptItemResultResponse(10L, true, "정답", "정답", null)));

    given(quizAttemptService.submit(eq(1L), eq(1L), any(AttemptCreateRequest.class)))
        .willReturn(response);

    mockMvc
        .perform(
            post("/api/quizzes/1/attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.attemptId").value(101))
        .andExpect(jsonPath("$.data.topPercentile").value(5.0));
  }

  @Test
  @DisplayName("submit_오답포함결과_correct필드false포함")
  void submit_mixedResults_incorrectFlagIncluded() throws Exception {
    AttemptCreateRequest request =
        new AttemptCreateRequest(
            List.of(new AttemptAnswerRequest(10L, "정답"), new AttemptAnswerRequest(11L, "틀림")), 30);

    AttemptResultResponse response =
        new AttemptResultResponse(
            50L,
            1,
            2,
            50.0,
            33.3,
            List.of(
                new AttemptItemResultResponse(10L, true, "정답", "정답", null),
                new AttemptItemResultResponse(
                    11L, false, "답2", "틀림", "https://signed.example/answer.png")));

    given(quizAttemptService.submit(eq(1L), eq(1L), any(AttemptCreateRequest.class)))
        .willReturn(response);

    mockMvc
        .perform(
            post("/api/quizzes/1/attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.score").value(1))
        .andExpect(jsonPath("$.data.totalQuestions").value(2))
        .andExpect(jsonPath("$.data.percent").value(50.0))
        .andExpect(jsonPath("$.data.results[1].correct").value(false))
        .andExpect(jsonPath("$.data.results[1].correctAnswer").value("답2"))
        .andExpect(jsonPath("$.data.results[0].correctAnswerImageUrl").isEmpty())
        .andExpect(
            jsonPath("$.data.results[1].correctAnswerImageUrl")
                .value("https://signed.example/answer.png"));
  }
}
