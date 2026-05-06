package com.ongodmatchu.domain.quiz.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.quiz.dto.QuizResponse;
import com.ongodmatchu.domain.quiz.dto.QuizUpdateRequest;
import com.ongodmatchu.domain.quiz.service.QuizService;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.time.LocalDateTime;
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

@WebMvcTest(QuizController.class)
@AutoConfigureMockMvc(addFilters = false)
class QuizControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private QuizService quizService;
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
            "퀴즈작성자",
            LocalDateTime.of(2024, 1, 1, 0, 0));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ============ PATCH /api/quizzes/{quizId} ============

  @Test
  @DisplayName("updateQuiz_정상요청_200_QuizResponse반환")
  void updateQuiz_validRequest_returns200WithQuizResponse() throws Exception {
    QuizUpdateRequest request = new QuizUpdateRequest("새 제목", "새 설명", "music", null);
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
            "퀴즈작성자",
            LocalDateTime.of(2024, 1, 1, 0, 0));
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
    QuizUpdateRequest request = new QuizUpdateRequest("새 제목", null, null, null);
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
    QuizUpdateRequest request = new QuizUpdateRequest("새 제목", null, null, null);
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
  @DisplayName("updateQuiz_모든필드null_정상_200반환")
  void updateQuiz_allNullFields_returns200() throws Exception {
    QuizUpdateRequest request = new QuizUpdateRequest(null, null, null, null);
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
}
