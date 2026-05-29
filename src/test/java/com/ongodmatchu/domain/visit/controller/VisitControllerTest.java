package com.ongodmatchu.domain.visit.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.visit.dto.VisitCreateRequest;
import com.ongodmatchu.domain.visit.service.VisitLogService;
import com.ongodmatchu.global.web.AnonIdCookieFilter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

@WebMvcTest(VisitController.class)
@AutoConfigureMockMvc(addFilters = false)
class VisitControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private VisitLogService visitLogService;
  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  private static final String ANON_ID = "anon-uuid";

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ============ POST /api/visits ============

  @Test
  @DisplayName("recordVisit_비로그인_anonId만_있을때_userId_null_전달")
  void recordVisit_anonymous_passesNullUserId() throws Exception {
    VisitCreateRequest request = new VisitCreateRequest("/quiz");
    willDoNothing().given(visitLogService).recordVisit(eq(ANON_ID), isNull(), eq("/quiz"));

    mockMvc
        .perform(
            post("/api/visits")
                .requestAttr(AnonIdCookieFilter.REQUEST_ATTRIBUTE, ANON_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    then(visitLogService).should().recordVisit(eq(ANON_ID), isNull(), eq("/quiz"));
  }

  @Test
  @DisplayName("recordVisit_로그인_anonId와_userId_함께_전달")
  void recordVisit_authenticated_passesUserId() throws Exception {
    User testUser =
        User.builder()
            .email("user@example.com")
            .nickname("방문자")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(testUser, "id", 42L);
    ReflectionTestUtils.setField(
        testUser, "publicId", UUID.fromString("00000000-0000-0000-0000-000000000042"));
    ReflectionTestUtils.setField(testUser, "termsVersion", "1.0");
    ReflectionTestUtils.setField(testUser, "privacyVersion", "1.0");

    CustomUserDetails userDetails = new CustomUserDetails(testUser);
    Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);

    VisitCreateRequest request = new VisitCreateRequest("/quiz/abc-123");
    willDoNothing().given(visitLogService).recordVisit(eq(ANON_ID), eq(42L), eq("/quiz/abc-123"));

    mockMvc
        .perform(
            post("/api/visits")
                .requestAttr(AnonIdCookieFilter.REQUEST_ATTRIBUTE, ANON_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    then(visitLogService).should().recordVisit(eq(ANON_ID), eq(42L), eq("/quiz/abc-123"));
  }

  @Test
  @DisplayName("recordVisit_anonId_request_attribute로_전달_AnonIdCookieFilter_REQUEST_ATTRIBUTE_상수사용")
  void recordVisit_anonIdViaRequestAttribute() throws Exception {
    VisitCreateRequest request = new VisitCreateRequest("/");
    String customAnonId = "test-anon-uuid";
    willDoNothing().given(visitLogService).recordVisit(eq(customAnonId), isNull(), eq("/"));

    mockMvc
        .perform(
            post("/api/visits")
                .requestAttr(AnonIdCookieFilter.REQUEST_ATTRIBUTE, customAnonId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    then(visitLogService).should().recordVisit(eq(customAnonId), isNull(), eq("/"));
  }

  @Test
  @DisplayName("recordVisit_path_빈문자열_400반환")
  void recordVisit_blankPath_returns400() throws Exception {
    String body = "{\"path\":\"\"}";

    mockMvc
        .perform(
            post("/api/visits")
                .requestAttr(AnonIdCookieFilter.REQUEST_ATTRIBUTE, ANON_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @DisplayName("recordVisit_path_256자_400반환")
  void recordVisit_pathTooLong_returns400() throws Exception {
    String tooLong = "/" + "a".repeat(255); // 256자
    VisitCreateRequest request = new VisitCreateRequest(tooLong);

    mockMvc
        .perform(
            post("/api/visits")
                .requestAttr(AnonIdCookieFilter.REQUEST_ATTRIBUTE, ANON_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @DisplayName("recordVisit_body_누락_400반환")
  void recordVisit_missingBody_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/visits")
                .requestAttr(AnonIdCookieFilter.REQUEST_ATTRIBUTE, ANON_ID)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }
}
