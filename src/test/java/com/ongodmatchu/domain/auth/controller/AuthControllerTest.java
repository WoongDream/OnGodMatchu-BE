package com.ongodmatchu.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongodmatchu.domain.auth.dto.NicknameAvailabilityResponse;
import com.ongodmatchu.domain.auth.dto.SendVerificationCodeRequest;
import com.ongodmatchu.domain.auth.dto.SignupRequest;
import com.ongodmatchu.domain.auth.dto.SignupResponse;
import com.ongodmatchu.domain.auth.dto.TokenResponse;
import com.ongodmatchu.domain.auth.service.AuthService;
import com.ongodmatchu.domain.user.dto.UserResponse;
import com.ongodmatchu.global.exception.RateLimitException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AuthService authService;
  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  // ===== send-verification-code =====

  @Test
  @DisplayName("코드발송_정상_200")
  void sendVerificationCode_success_returns200() throws Exception {
    String body = objectMapper.writeValueAsString(new SendVerificationCodeRequest("u@example.com"));

    mockMvc
        .perform(
            post("/api/auth/send-verification-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("코드발송_rate_limit_429_retryAfter_헤더_바디포함")
  void sendVerificationCode_rateLimited_returns429WithRetryAfter() throws Exception {
    willThrow(new RateLimitException(60))
        .given(authService)
        .requestVerificationCode(anyString(), anyString());

    String body = objectMapper.writeValueAsString(new SendVerificationCodeRequest("u@example.com"));

    mockMvc
        .perform(
            post("/api/auth/send-verification-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
        .andExpect(jsonPath("$.error.retryAfter").value(60));
  }

  @Test
  @DisplayName("코드발송_이메일형식위반_400")
  void sendVerificationCode_invalidEmail_returns400() throws Exception {
    String body = "{\"email\":\"not-an-email\"}";

    mockMvc
        .perform(
            post("/api/auth/send-verification-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  // ===== signup =====

  @Test
  @DisplayName("회원가입_정상_201_user_및_토큰포함")
  void signup_success_returns201WithTokens() throws Exception {
    SignupResponse response =
        new SignupResponse(
            new UserResponse(
                java.util.UUID.randomUUID(),
                "닉네임",
                "u@example.com",
                "https://cdn.example.com/default.png",
                null,
                java.time.OffsetDateTime.now(),
                0L,
                true,
                "LOCAL"),
            "AT",
            "RT");
    given(authService.signup(any(SignupRequest.class))).willReturn(response);

    String body =
        objectMapper.writeValueAsString(
            new SignupRequest("u@example.com", "닉네임", "password123", "123456"));

    mockMvc
        .perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("AT"))
        .andExpect(jsonPath("$.data.refreshToken").value("RT"))
        .andExpect(jsonPath("$.data.user.userId").exists())
        .andExpect(jsonPath("$.data.user.email").value("u@example.com"))
        .andExpect(jsonPath("$.data.user.nickname").value("닉네임"));
  }

  @Test
  @DisplayName("회원가입_코드누락_400")
  void signup_missingCode_returns400() throws Exception {
    String body = "{\"email\":\"u@example.com\",\"nickname\":\"닉네임\",\"password\":\"password123\"}";

    mockMvc
        .perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @DisplayName("회원가입_코드형식위반_6자리아님_400")
  void signup_invalidCodeFormat_returns400() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new SignupRequest("u@example.com", "닉네임", "password123", "abc"));

    mockMvc
        .perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  // ===== login =====

  @Test
  @DisplayName("로그인_정상_200_토큰반환")
  void login_success_returns200WithTokens() throws Exception {
    given(authService.login(any())).willReturn(new TokenResponse("AT", "RT"));

    String body = "{\"email\":\"u@example.com\",\"password\":\"password123\"}";

    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("AT"))
        .andExpect(jsonPath("$.data.refreshToken").value("RT"));
  }

  // ===== check-nickname (기존 케이스 유지) =====

  @Test
  @DisplayName("checkNickname_사용가능한닉네임_available_true반환")
  void checkNickname_available_returns200WithAvailableTrue() throws Exception {
    given(authService.checkNicknameAvailability("available_one"))
        .willReturn(NicknameAvailabilityResponse.AVAILABLE);

    mockMvc
        .perform(get("/api/auth/check-nickname").param("nickname", "available_one"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.available").value(true))
        .andExpect(jsonPath("$.data.reason").doesNotExist());
  }

  @Test
  @DisplayName("checkNickname_형식위반닉네임_available_false_reason_format")
  void checkNickname_invalidFormat_returns200WithFormatReason() throws Exception {
    given(authService.checkNicknameAvailability("tooshort"))
        .willReturn(
            NicknameAvailabilityResponse.unavailable(NicknameAvailabilityResponse.REASON_FORMAT));

    mockMvc
        .perform(get("/api/auth/check-nickname").param("nickname", "tooshort"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.available").value(false))
        .andExpect(jsonPath("$.data.reason").value("format"));
  }

  @Test
  @DisplayName("checkNickname_중복닉네임_available_false_reason_taken")
  void checkNickname_duplicate_returns200WithTakenReason() throws Exception {
    given(authService.checkNicknameAvailability("takenNick"))
        .willReturn(
            NicknameAvailabilityResponse.unavailable(
                NicknameAvailabilityResponse.REASON_DUPLICATE));

    mockMvc
        .perform(get("/api/auth/check-nickname").param("nickname", "takenNick"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.available").value(false))
        .andExpect(jsonPath("$.data.reason").value("taken"));
  }

  @Test
  @DisplayName("checkNickname_파라미터누락_4xx반환")
  void checkNickname_missingParam_returns4xx() throws Exception {
    mockMvc
        .perform(get("/api/auth/check-nickname"))
        .andExpect(
            result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));
  }
}
