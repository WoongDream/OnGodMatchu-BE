package com.ongodmatchu.domain.notice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongodmatchu.domain.notice.dto.NoticeDetailResponse;
import com.ongodmatchu.domain.notice.dto.NoticeListItemResponse;
import com.ongodmatchu.domain.notice.entity.NoticeType;
import com.ongodmatchu.domain.notice.service.NoticeService;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
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

  private static final OffsetDateTime SAMPLE_PUBLISHED_AT =
      OffsetDateTime.of(2024, 5, 1, 12, 0, 0, 0, ZoneOffset.of("+09:00"));

  // ============ GET /api/announcements ============

  @Test
  @DisplayName("getAnnouncements_정상요청_200_NoticeType_ANNOUNCEMENT로_조회")
  void getAnnouncements_validRequest_returns200AndCallsServiceWithAnnouncementType()
      throws Exception {
    NoticeListItemResponse item = new NoticeListItemResponse(1L, "공지사항 제목", SAMPLE_PUBLISHED_AT);
    Page<NoticeListItemResponse> page = new PageImpl<>(List.of(item));
    given(noticeService.getList(any(NoticeType.class), any(Pageable.class))).willReturn(page);

    mockMvc
        .perform(get("/api/announcements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].id").value(1L))
        .andExpect(jsonPath("$.data.content[0].title").value("공지사항 제목"))
        .andExpect(jsonPath("$.data.content[0].publishedAt").exists());

    ArgumentCaptor<NoticeType> typeCaptor = ArgumentCaptor.forClass(NoticeType.class);
    verify(noticeService).getList(typeCaptor.capture(), any(Pageable.class));
    assertThat(typeCaptor.getValue()).isEqualTo(NoticeType.ANNOUNCEMENT);
  }

  @Test
  @DisplayName("getAnnouncements_빈페이지_200_빈배열반환")
  void getAnnouncements_emptyPage_returns200WithEmptyContent() throws Exception {
    given(noticeService.getList(any(NoticeType.class), any(Pageable.class)))
        .willReturn(Page.empty());

    mockMvc
        .perform(get("/api/announcements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content").isEmpty());
  }

  // ============ GET /api/announcements/{noticeId} ============

  @Test
  @DisplayName("getAnnouncementDetail_정상요청_200_NoticeDetailResponse반환")
  void getAnnouncementDetail_validRequest_returns200WithNoticeDetail() throws Exception {
    NoticeDetailResponse detail =
        new NoticeDetailResponse(1L, "공지사항 제목", "## 본문", SAMPLE_PUBLISHED_AT);
    given(noticeService.getDetail(eq(NoticeType.ANNOUNCEMENT), eq(1L))).willReturn(detail);

    mockMvc
        .perform(get("/api/announcements/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(1L))
        .andExpect(jsonPath("$.data.title").value("공지사항 제목"))
        .andExpect(jsonPath("$.data.content").value("## 본문"))
        .andExpect(jsonPath("$.data.publishedAt").exists());

    verify(noticeService).getDetail(eq(NoticeType.ANNOUNCEMENT), eq(1L));
  }

  @Test
  @DisplayName("getAnnouncementDetail_미존재_404_NOTICE_NOT_FOUND반환")
  void getAnnouncementDetail_notFound_returns404() throws Exception {
    given(noticeService.getDetail(eq(NoticeType.ANNOUNCEMENT), eq(99L)))
        .willThrow(new BusinessException(ErrorCode.NOTICE_NOT_FOUND));

    mockMvc
        .perform(get("/api/announcements/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("NOTICE_NOT_FOUND"));
  }

  // ============ GET /api/release-notes ============

  @Test
  @DisplayName("getReleaseNotes_정상요청_200_NoticeType_RELEASE_NOTE로_조회")
  void getReleaseNotes_validRequest_returns200AndCallsServiceWithReleaseNoteType()
      throws Exception {
    NoticeListItemResponse item = new NoticeListItemResponse(2L, "릴리즈 노트 제목", SAMPLE_PUBLISHED_AT);
    Page<NoticeListItemResponse> page = new PageImpl<>(List.of(item));
    given(noticeService.getList(any(NoticeType.class), any(Pageable.class))).willReturn(page);

    mockMvc
        .perform(get("/api/release-notes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].id").value(2L))
        .andExpect(jsonPath("$.data.content[0].title").value("릴리즈 노트 제목"));

    ArgumentCaptor<NoticeType> typeCaptor = ArgumentCaptor.forClass(NoticeType.class);
    verify(noticeService).getList(typeCaptor.capture(), any(Pageable.class));
    assertThat(typeCaptor.getValue()).isEqualTo(NoticeType.RELEASE_NOTE);
  }

  // ============ GET /api/release-notes/{noticeId} ============

  @Test
  @DisplayName("getReleaseNoteDetail_정상요청_200_NoticeDetailResponse반환")
  void getReleaseNoteDetail_validRequest_returns200WithNoticeDetail() throws Exception {
    NoticeDetailResponse detail =
        new NoticeDetailResponse(2L, "릴리즈 노트 제목", "## 변경 사항", SAMPLE_PUBLISHED_AT);
    given(noticeService.getDetail(eq(NoticeType.RELEASE_NOTE), eq(2L))).willReturn(detail);

    mockMvc
        .perform(get("/api/release-notes/2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(2L))
        .andExpect(jsonPath("$.data.title").value("릴리즈 노트 제목"))
        .andExpect(jsonPath("$.data.content").value("## 변경 사항"))
        .andExpect(jsonPath("$.data.publishedAt").exists());

    verify(noticeService).getDetail(eq(NoticeType.RELEASE_NOTE), eq(2L));
  }

  @Test
  @DisplayName("getReleaseNoteDetail_미존재_404_NOTICE_NOT_FOUND반환")
  void getReleaseNoteDetail_notFound_returns404() throws Exception {
    given(noticeService.getDetail(eq(NoticeType.RELEASE_NOTE), eq(99L)))
        .willThrow(new BusinessException(ErrorCode.NOTICE_NOT_FOUND));

    mockMvc
        .perform(get("/api/release-notes/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("NOTICE_NOT_FOUND"));
  }
}
