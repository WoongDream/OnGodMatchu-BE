package com.ongodmatchu.domain.notice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongodmatchu.domain.notice.dto.NoticeDetailResponse;
import com.ongodmatchu.domain.notice.dto.NoticeListItemResponse;
import com.ongodmatchu.domain.notice.service.NoticeService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NoticeController.class)
@AutoConfigureMockMvc(addFilters = false)
class NoticeControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private NoticeService noticeService;
  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  // ============ GET /api/announcements ============

  @Test
  @DisplayName("getAnnouncements_정상_200_Page반환_서비스위임")
  void getAnnouncements_returns200WithPage() throws Exception {
    NoticeListItemResponse item =
        new NoticeListItemResponse(
            1L,
            "공지 제목",
            true,
            OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
            OffsetDateTime.of(2025, 1, 2, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
            42L);
    Page<NoticeListItemResponse> page = new PageImpl<>(List.of(item));
    given(noticeService.getAnnouncements(any(Pageable.class))).willReturn(page);

    mockMvc
        .perform(get("/api/announcements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("공지 제목"))
        .andExpect(jsonPath("$.data.content[0].pinned").value(true))
        .andExpect(jsonPath("$.data.content[0].viewCount").value(42))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    org.mockito.Mockito.verify(noticeService).getAnnouncements(any(Pageable.class));
  }

  @Test
  @DisplayName("getAnnouncements_파라미터없음_기본size20으로_위임")
  void getAnnouncements_noParam_passesDefaultSize20() throws Exception {
    given(noticeService.getAnnouncements(any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc.perform(get("/api/announcements")).andExpect(status().isOk());

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    org.mockito.Mockito.verify(noticeService).getAnnouncements(captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(20);
  }

  @Test
  @DisplayName("getAnnouncements_빈목록_200_빈페이지반환")
  void getAnnouncements_empty_returnsEmptyPage() throws Exception {
    given(noticeService.getAnnouncements(any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/api/announcements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  // ============ GET /api/announcements/{id} ============

  @Test
  @DisplayName("getAnnouncementDetail_정상_200_상세필드노출_id위임")
  void getAnnouncementDetail_returns200WithDetailFields() throws Exception {
    NoticeDetailResponse detail =
        new NoticeDetailResponse(
            7L,
            "상세 제목",
            "본문 내용입니다.",
            false,
            OffsetDateTime.of(2025, 3, 10, 9, 0, 0, 0, ZoneOffset.of("+09:00")),
            OffsetDateTime.of(2025, 3, 11, 9, 0, 0, 0, ZoneOffset.of("+09:00")),
            100L);
    given(noticeService.getAnnouncementDetail(eq(7L))).willReturn(detail);

    mockMvc
        .perform(get("/api/announcements/{id}", 7L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(7))
        .andExpect(jsonPath("$.data.title").value("상세 제목"))
        .andExpect(jsonPath("$.data.content").value("본문 내용입니다."))
        .andExpect(jsonPath("$.data.pinned").value(false))
        .andExpect(jsonPath("$.data.publishedAt").exists())
        .andExpect(jsonPath("$.data.viewCount").value(100));

    org.mockito.Mockito.verify(noticeService).getAnnouncementDetail(eq(7L));
  }
}
