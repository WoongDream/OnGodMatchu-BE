package com.ongodmatchu.domain.notice.controller;

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
import com.ongodmatchu.domain.notice.dto.AdminNoticeListItemResponse;
import com.ongodmatchu.domain.notice.dto.AdminNoticeResponse;
import com.ongodmatchu.domain.notice.dto.NoticeStatsResponse;
import com.ongodmatchu.domain.notice.dto.NoticeUpdateRequest;
import com.ongodmatchu.domain.notice.entity.NoticeStatus;
import com.ongodmatchu.domain.notice.service.AdminNoticeService;
import com.ongodmatchu.domain.notice.service.NoticeFilter;
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

@WebMvcTest(AdminNoticeController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminNoticeControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AdminNoticeService adminNoticeService;
  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  private AdminNoticeResponse sampleResponse() {
    return new AdminNoticeResponse(
        5L,
        "관리 공지",
        "본문",
        "PUBLISHED",
        true,
        12L,
        OffsetDateTime.of(2025, 2, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
        OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
        null);
  }

  // ============ GET /api/admin/notices ============

  @Test
  @DisplayName("getNotices_필터없음_200_filter기본ALL/query null로위임")
  void getNotices_noFilter_passesAllAndNullQuery() throws Exception {
    AdminNoticeListItemResponse item =
        new AdminNoticeListItemResponse(
            5L,
            "관리 공지",
            "PUBLISHED",
            true,
            12L,
            OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")));
    Page<AdminNoticeListItemResponse> page = new PageImpl<>(List.of(item));
    given(adminNoticeService.getNotices(eq(NoticeFilter.ALL), isNull(), any(Pageable.class)))
        .willReturn(page);

    mockMvc
        .perform(get("/api/admin/notices"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value(5))
        .andExpect(jsonPath("$.data.content[0].title").value("관리 공지"))
        .andExpect(jsonPath("$.data.content[0].status").value("PUBLISHED"))
        .andExpect(jsonPath("$.data.content[0].pinned").value(true))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    ArgumentCaptor<NoticeFilter> filterCaptor = ArgumentCaptor.forClass(NoticeFilter.class);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(adminNoticeService)
        .getNotices(filterCaptor.capture(), queryCaptor.capture(), any(Pageable.class));
    assertThat(filterCaptor.getValue()).isEqualTo(NoticeFilter.ALL);
    assertThat(queryCaptor.getValue()).isNull();
  }

  @Test
  @DisplayName("getNotices_filter=PINNED+query지정_enum변환후위임")
  void getNotices_pinnedFilterWithQuery_convertsEnumAndDelegates() throws Exception {
    given(adminNoticeService.getNotices(eq(NoticeFilter.PINNED), eq("이벤트"), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/api/admin/notices").param("filter", "PINNED").param("query", "이벤트"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    ArgumentCaptor<NoticeFilter> filterCaptor = ArgumentCaptor.forClass(NoticeFilter.class);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(adminNoticeService)
        .getNotices(filterCaptor.capture(), queryCaptor.capture(), any(Pageable.class));
    assertThat(filterCaptor.getValue()).isEqualTo(NoticeFilter.PINNED);
    assertThat(queryCaptor.getValue()).isEqualTo("이벤트");
  }

  // ============ GET /api/admin/notices/stats ============

  @Test
  @DisplayName("getStats_정상_200_통계필드노출_서비스위임")
  void getStats_returns200WithStats() throws Exception {
    given(adminNoticeService.getStats()).willReturn(new NoticeStatsResponse(10L, 4L, 3L, 3L));

    mockMvc
        .perform(get("/api/admin/notices/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.total").value(10))
        .andExpect(jsonPath("$.data.published").value(4))
        .andExpect(jsonPath("$.data.pinned").value(3))
        .andExpect(jsonPath("$.data.draft").value(3));

    org.mockito.Mockito.verify(adminNoticeService).getStats();
  }

  // ============ GET /api/admin/notices/{id} ============

  @Test
  @DisplayName("getNotice_정상_200_단건반환_id위임_stats와라우트충돌없음")
  void getNotice_returns200AndDelegatesId() throws Exception {
    given(adminNoticeService.getNotice(eq(5L))).willReturn(sampleResponse());

    mockMvc
        .perform(get("/api/admin/notices/{id}", 5L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(5))
        .andExpect(jsonPath("$.data.title").value("관리 공지"))
        .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
        .andExpect(jsonPath("$.data.content").value("본문"));

    org.mockito.Mockito.verify(adminNoticeService).getNotice(eq(5L));
  }

  // ============ POST /api/admin/notices ============

  @Test
  @DisplayName("create_정상요청_200_request위임")
  void create_validRequest_returns200AndDelegates() throws Exception {
    String body =
        "{\"title\":\"새 공지\",\"content\":\"새 본문\",\"status\":\"PUBLISHED\",\"pinned\":true}";
    given(adminNoticeService.create(any())).willReturn(sampleResponse());

    mockMvc
        .perform(post("/api/admin/notices").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(5));

    org.mockito.Mockito.verify(adminNoticeService).create(any());
  }

  @Test
  @DisplayName("create_title공백_400_서비스미호출")
  void create_blankTitle_returns400() throws Exception {
    String body = "{\"title\":\"\",\"content\":\"본문\"}";

    mockMvc
        .perform(post("/api/admin/notices").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(adminNoticeService);
  }

  @Test
  @DisplayName("create_content공백_400_서비스미호출")
  void create_blankContent_returns400() throws Exception {
    String body = "{\"title\":\"제목\",\"content\":\"\"}";

    mockMvc
        .perform(post("/api/admin/notices").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(adminNoticeService);
  }

  // ============ PATCH /api/admin/notices/{id} ============

  @Test
  @DisplayName("update_정상요청_200_id/request정확히위임")
  void update_validRequest_returns200AndDelegates() throws Exception {
    String body =
        "{\"title\":\"수정 제목\",\"content\":\"수정 본문\",\"status\":\"DRAFT\",\"pinned\":false}";
    given(adminNoticeService.update(eq(5L), any(NoticeUpdateRequest.class)))
        .willReturn(sampleResponse());

    mockMvc
        .perform(
            patch("/api/admin/notices/{id}", 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(5));

    ArgumentCaptor<NoticeUpdateRequest> captor = ArgumentCaptor.forClass(NoticeUpdateRequest.class);
    org.mockito.Mockito.verify(adminNoticeService).update(eq(5L), captor.capture());
    NoticeUpdateRequest captured = captor.getValue();
    assertThat(captured.title()).isEqualTo("수정 제목");
    assertThat(captured.content()).isEqualTo("수정 본문");
    assertThat(captured.status()).isEqualTo(NoticeStatus.DRAFT);
    assertThat(captured.pinned()).isFalse();
  }

  // ============ DELETE /api/admin/notices/{id} ============

  @Test
  @DisplayName("delete_정상_200_id위임")
  void delete_returns200AndDelegatesId() throws Exception {
    willDoNothing().given(adminNoticeService).delete(eq(5L));

    mockMvc
        .perform(delete("/api/admin/notices/{id}", 5L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    org.mockito.Mockito.verify(adminNoticeService).delete(eq(5L));
  }
}
