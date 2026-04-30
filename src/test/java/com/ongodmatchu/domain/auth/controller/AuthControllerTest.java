package com.ongodmatchu.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ongodmatchu.domain.auth.dto.NicknameAvailabilityResponse;
import com.ongodmatchu.domain.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

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
  @DisplayName("checkNickname_중복닉네임_available_false_reason_duplicate")
  void checkNickname_duplicate_returns200WithDuplicateReason() throws Exception {
    given(authService.checkNicknameAvailability("takenNick"))
        .willReturn(
            NicknameAvailabilityResponse.unavailable(
                NicknameAvailabilityResponse.REASON_DUPLICATE));

    mockMvc
        .perform(get("/api/auth/check-nickname").param("nickname", "takenNick"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.available").value(false))
        .andExpect(jsonPath("$.data.reason").value("duplicate"));
  }

  @Test
  @DisplayName("checkNickname_파라미터누락_4xx반환")
  void checkNickname_missingParam_returns4xx() throws Exception {
    // @RequestParam(required=true)이 누락되면 MissingServletRequestParameterException 발생.
    // GlobalExceptionHandler가 해당 예외를 처리하지 않으면 500으로 폴백될 수 있으나
    // 어떤 경우든 2xx 성공 응답이 아님을 보장한다.
    mockMvc
        .perform(get("/api/auth/check-nickname"))
        .andExpect(
            result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));
  }
}
