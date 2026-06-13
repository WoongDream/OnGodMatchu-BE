package com.ongodmatchu.domain.nickname.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongodmatchu.domain.nickname.dto.ForbiddenNicknameCreateRequest;
import com.ongodmatchu.domain.nickname.dto.ForbiddenNicknameResponse;
import com.ongodmatchu.domain.nickname.dto.ForbiddenNicknameUpdateRequest;
import com.ongodmatchu.domain.nickname.dto.NicknameRuleStatsResponse;
import com.ongodmatchu.domain.nickname.entity.ForbiddenNicknameType;
import com.ongodmatchu.domain.nickname.entity.NicknameMatchType;
import com.ongodmatchu.domain.nickname.service.ForbiddenNicknameService;
import com.ongodmatchu.domain.nickname.service.NicknameRuleFilter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminNicknameController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminNicknameControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ForbiddenNicknameService forbiddenNicknameService;
  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  private ForbiddenNicknameResponse sampleResponse() {
    return new ForbiddenNicknameResponse(
        5L,
        "관리자",
        "관리자",
        "RESERVED",
        "EXACT",
        "사칭 방지",
        OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")));
  }

  // ============ GET /api/admin/nicknames ============

  @Test
  @DisplayName("getRules_필터없음_200_filter기본ALL/query null로위임")
  void getRules_noFilter_passesAllAndNullQuery() throws Exception {
    Page<ForbiddenNicknameResponse> page = new PageImpl<>(List.of(sampleResponse()));
    given(
            forbiddenNicknameService.getRules(
                eq(NicknameRuleFilter.ALL), isNull(), any(Pageable.class)))
        .willReturn(page);

    mockMvc
        .perform(get("/api/admin/nicknames"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value(5))
        .andExpect(jsonPath("$.data.content[0].value").value("관리자"))
        .andExpect(jsonPath("$.data.content[0].type").value("RESERVED"))
        .andExpect(jsonPath("$.data.content[0].matchType").value("EXACT"))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    ArgumentCaptor<NicknameRuleFilter> filterCaptor =
        ArgumentCaptor.forClass(NicknameRuleFilter.class);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(forbiddenNicknameService)
        .getRules(filterCaptor.capture(), queryCaptor.capture(), any(Pageable.class));
    assertThat(filterCaptor.getValue()).isEqualTo(NicknameRuleFilter.ALL);
    assertThat(queryCaptor.getValue()).isNull();
  }

  @Test
  @DisplayName("getRules_filter=FORBIDDEN+query지정_enum변환후위임")
  void getRules_forbiddenFilterWithQuery_convertsEnumAndDelegates() throws Exception {
    given(
            forbiddenNicknameService.getRules(
                eq(NicknameRuleFilter.FORBIDDEN), eq("병"), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/api/admin/nicknames").param("filter", "FORBIDDEN").param("query", "병"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    org.mockito.Mockito.verify(forbiddenNicknameService)
        .getRules(eq(NicknameRuleFilter.FORBIDDEN), eq("병"), any(Pageable.class));
  }

  // ============ GET /api/admin/nicknames/stats ============

  @Test
  @DisplayName("getStats_정상_200_통계필드노출_서비스위임")
  void getStats_returns200WithStats() throws Exception {
    given(forbiddenNicknameService.getStats())
        .willReturn(new NicknameRuleStatsResponse(10L, 4L, 6L));

    mockMvc
        .perform(get("/api/admin/nicknames/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.total").value(10))
        .andExpect(jsonPath("$.data.forbidden").value(4))
        .andExpect(jsonPath("$.data.reserved").value(6));

    org.mockito.Mockito.verify(forbiddenNicknameService).getStats();
  }

  // ============ GET /api/admin/nicknames/{id} ============

  @Test
  @DisplayName("getRule_정상_200_단건반환_id위임")
  void getRule_returns200AndDelegatesId() throws Exception {
    given(forbiddenNicknameService.getRule(eq(5L))).willReturn(sampleResponse());

    mockMvc
        .perform(get("/api/admin/nicknames/{id}", 5L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(5))
        .andExpect(jsonPath("$.data.value").value("관리자"))
        .andExpect(jsonPath("$.data.reason").value("사칭 방지"));

    org.mockito.Mockito.verify(forbiddenNicknameService).getRule(eq(5L));
  }

  // ============ POST /api/admin/nicknames ============

  @Test
  @DisplayName("create_정상요청_200_request위임")
  void create_validRequest_returns200AndDelegates() throws Exception {
    String body =
        "{\"value\":\"관리자\",\"type\":\"RESERVED\",\"matchType\":\"EXACT\",\"reason\":\"사칭 방지\"}";
    given(forbiddenNicknameService.create(any())).willReturn(sampleResponse());

    mockMvc
        .perform(post("/api/admin/nicknames").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(5));

    ArgumentCaptor<ForbiddenNicknameCreateRequest> captor =
        ArgumentCaptor.forClass(ForbiddenNicknameCreateRequest.class);
    org.mockito.Mockito.verify(forbiddenNicknameService).create(captor.capture());
    ForbiddenNicknameCreateRequest captured = captor.getValue();
    assertThat(captured.value()).isEqualTo("관리자");
    assertThat(captured.type()).isEqualTo(ForbiddenNicknameType.RESERVED);
    assertThat(captured.matchType()).isEqualTo(NicknameMatchType.EXACT);
  }

  @Test
  @DisplayName("create_value공백_400_서비스미호출")
  void create_blankValue_returns400() throws Exception {
    String body = "{\"value\":\"\",\"type\":\"RESERVED\",\"matchType\":\"EXACT\"}";

    mockMvc
        .perform(post("/api/admin/nicknames").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(forbiddenNicknameService);
  }

  @Test
  @DisplayName("create_matchType누락_400_서비스미호출")
  void create_missingMatchType_returns400() throws Exception {
    String body = "{\"value\":\"관리자\",\"type\":\"RESERVED\"}";

    mockMvc
        .perform(post("/api/admin/nicknames").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(forbiddenNicknameService);
  }

  // ============ PATCH /api/admin/nicknames/{id} ============

  @Test
  @DisplayName("update_정상요청_200_id/request정확히위임")
  void update_validRequest_returns200AndDelegates() throws Exception {
    String body =
        "{\"value\":\"운영자\",\"type\":\"RESERVED\",\"matchType\":\"PREFIX\",\"reason\":\"수정\"}";
    given(forbiddenNicknameService.update(eq(5L), any(ForbiddenNicknameUpdateRequest.class)))
        .willReturn(sampleResponse());

    mockMvc
        .perform(
            patch("/api/admin/nicknames/{id}", 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(5));

    ArgumentCaptor<ForbiddenNicknameUpdateRequest> captor =
        ArgumentCaptor.forClass(ForbiddenNicknameUpdateRequest.class);
    org.mockito.Mockito.verify(forbiddenNicknameService).update(eq(5L), captor.capture());
    ForbiddenNicknameUpdateRequest captured = captor.getValue();
    assertThat(captured.value()).isEqualTo("운영자");
    assertThat(captured.type()).isEqualTo(ForbiddenNicknameType.RESERVED);
    assertThat(captured.matchType()).isEqualTo(NicknameMatchType.PREFIX);
  }

  @Test
  @DisplayName("update_value공백_400_서비스미호출")
  void update_blankValue_returns400() throws Exception {
    String body = "{\"value\":\"\",\"type\":\"RESERVED\",\"matchType\":\"EXACT\"}";

    mockMvc
        .perform(
            patch("/api/admin/nicknames/{id}", 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(forbiddenNicknameService);
  }

  // ============ DELETE /api/admin/nicknames/{id} ============

  @Test
  @DisplayName("delete_정상_200_id위임")
  void delete_returns200AndDelegatesId() throws Exception {
    willDoNothing().given(forbiddenNicknameService).delete(eq(5L));

    mockMvc
        .perform(delete("/api/admin/nicknames/{id}", 5L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    org.mockito.Mockito.verify(forbiddenNicknameService).delete(eq(5L));
  }
}
