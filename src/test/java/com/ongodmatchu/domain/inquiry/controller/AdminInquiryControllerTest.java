package com.ongodmatchu.domain.inquiry.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.inquiry.dto.AdminInquiryDetailResponse;
import com.ongodmatchu.domain.inquiry.dto.AdminInquiryListItemResponse;
import com.ongodmatchu.domain.inquiry.dto.InquiryAuthorResponse;
import com.ongodmatchu.domain.inquiry.dto.InquiryStatsResponse;
import com.ongodmatchu.domain.inquiry.entity.InquiryStatus;
import com.ongodmatchu.domain.inquiry.service.AdminInquiryService;
import com.ongodmatchu.domain.inquiry.service.InquiryFilter;
import com.ongodmatchu.domain.notification.dto.NotificationResponse;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.Role;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminInquiryController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminInquiryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AdminInquiryService adminInquiryService;
  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  @BeforeEach
  void setUp() {
    // actor(ADMIN) 를 @AuthenticationPrincipal 로 주입 — answer 가 actor.getUser().getId() 를 꺼낸다.
    User actor =
        User.builder()
            .email("admin@example.com")
            .nickname("관리자")
            .password("hashed")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(actor, "id", 1L);
    ReflectionTestUtils.setField(
        actor, "publicId", UUID.fromString("00000000-0000-0000-0000-000000000001"));
    ReflectionTestUtils.setField(actor, "role", Role.ADMIN);
    ReflectionTestUtils.setField(actor, "termsVersion", "1.0");
    ReflectionTestUtils.setField(actor, "privacyVersion", "1.0");

    CustomUserDetails actorDetails = new CustomUserDetails(actor);
    Authentication auth = new UsernamePasswordAuthenticationToken(actorDetails, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private InquiryAuthorResponse sampleAuthor() {
    return new InquiryAuthorResponse(
        UUID.fromString("00000000-0000-0000-0000-000000000002"), "문의유저", "https://img/profile.svg");
  }

  private AdminInquiryDetailResponse sampleDetail() {
    return new AdminInquiryDetailResponse(
        7L,
        "결제 문의",
        "환불이 안돼요",
        sampleAuthor(),
        "PENDING",
        "대기",
        OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
        List.of());
  }

  // ============ GET /api/admin/inquiries ============

  @Test
  @DisplayName("getInquiries_필터없음_200_filter기본ALL/query null로위임")
  void getInquiries_noFilter_passesAllAndNullQuery() throws Exception {
    AdminInquiryListItemResponse item =
        new AdminInquiryListItemResponse(
            7L,
            "결제 문의",
            sampleAuthor(),
            "PENDING",
            "대기",
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")));
    Page<AdminInquiryListItemResponse> page = new PageImpl<>(List.of(item));
    given(adminInquiryService.getInquiries(eq(InquiryFilter.ALL), isNull(), any(Pageable.class)))
        .willReturn(page);

    mockMvc
        .perform(get("/api/admin/inquiries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value(7))
        .andExpect(jsonPath("$.data.content[0].title").value("결제 문의"))
        .andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
        .andExpect(jsonPath("$.data.content[0].author.nickname").value("문의유저"))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    ArgumentCaptor<InquiryFilter> filterCaptor = ArgumentCaptor.forClass(InquiryFilter.class);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(adminInquiryService)
        .getInquiries(filterCaptor.capture(), queryCaptor.capture(), any(Pageable.class));
    assertThat(filterCaptor.getValue()).isEqualTo(InquiryFilter.ALL);
    assertThat(queryCaptor.getValue()).isNull();
  }

  @Test
  @DisplayName("getInquiries_filter=PENDING+query지정_enum변환후위임")
  void getInquiries_pendingFilterWithQuery_convertsEnumAndDelegates() throws Exception {
    given(
            adminInquiryService.getInquiries(
                eq(InquiryFilter.PENDING), eq("환불"), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/api/admin/inquiries").param("filter", "PENDING").param("query", "환불"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    ArgumentCaptor<InquiryFilter> filterCaptor = ArgumentCaptor.forClass(InquiryFilter.class);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(adminInquiryService)
        .getInquiries(filterCaptor.capture(), queryCaptor.capture(), any(Pageable.class));
    assertThat(filterCaptor.getValue()).isEqualTo(InquiryFilter.PENDING);
    assertThat(queryCaptor.getValue()).isEqualTo("환불");
  }

  // ============ GET /api/admin/inquiries/stats ============

  @Test
  @DisplayName("getStats_정상_200_통계필드노출_서비스위임")
  void getStats_returns200WithStats() throws Exception {
    given(adminInquiryService.getStats()).willReturn(new InquiryStatsResponse(10L, 4L, 3L, 3L));

    mockMvc
        .perform(get("/api/admin/inquiries/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.total").value(10))
        .andExpect(jsonPath("$.data.pending").value(4))
        .andExpect(jsonPath("$.data.inProgress").value(3))
        .andExpect(jsonPath("$.data.done").value(3));

    org.mockito.Mockito.verify(adminInquiryService).getStats();
  }

  // ============ GET /api/admin/inquiries/{id} ============

  @Test
  @DisplayName("getInquiry_정상_200_단건반환_id위임_stats와라우트충돌없음")
  void getInquiry_returns200AndDelegatesId() throws Exception {
    given(adminInquiryService.getInquiry(eq(7L))).willReturn(sampleDetail());

    mockMvc
        .perform(get("/api/admin/inquiries/{id}", 7L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(7))
        .andExpect(jsonPath("$.data.title").value("결제 문의"))
        .andExpect(jsonPath("$.data.content").value("환불이 안돼요"))
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.author.nickname").value("문의유저"));

    org.mockito.Mockito.verify(adminInquiryService).getInquiry(eq(7L));
  }

  @Test
  @DisplayName("getInquiry_없는id_서비스INQUIRY_NOT_FOUND_404")
  void getInquiry_notFound_returns404() throws Exception {
    given(adminInquiryService.getInquiry(eq(99L)))
        .willThrow(new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));

    mockMvc
        .perform(get("/api/admin/inquiries/{id}", 99L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INQUIRY_NOT_FOUND"));

    org.mockito.Mockito.verify(adminInquiryService).getInquiry(eq(99L));
  }

  // ============ PATCH /api/admin/inquiries/{id}/status ============

  @Test
  @DisplayName("changeStatus_정상요청_200_id/status정확히위임")
  void changeStatus_validRequest_returns200AndDelegates() throws Exception {
    String body = "{\"status\":\"IN_PROGRESS\"}";
    given(adminInquiryService.changeStatus(eq(7L), eq(InquiryStatus.IN_PROGRESS)))
        .willReturn(sampleDetail());

    mockMvc
        .perform(
            patch("/api/admin/inquiries/{id}/status", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(7));

    org.mockito.Mockito.verify(adminInquiryService)
        .changeStatus(eq(7L), eq(InquiryStatus.IN_PROGRESS));
  }

  @Test
  @DisplayName("changeStatus_status누락_400_서비스미호출")
  void changeStatus_missingStatus_returns400() throws Exception {
    String body = "{}";

    mockMvc
        .perform(
            patch("/api/admin/inquiries/{id}/status", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(adminInquiryService);
  }

  // ============ POST /api/admin/inquiries/{id}/answers ============

  @Test
  @DisplayName("answer_정상요청_200_actorId/inquiryId/request위임")
  void answer_validRequest_returns200AndDelegates() throws Exception {
    String body = "{\"type\":\"INFO\",\"title\":\"답변 제목\",\"content\":\"답변 내용\"}";
    NotificationResponse n =
        new NotificationResponse(
            20L,
            "INFO",
            "안내",
            "답변 제목",
            "답변 내용",
            "운영팀",
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")));
    given(adminInquiryService.answer(eq(1L), eq(7L), any())).willReturn(n);

    mockMvc
        .perform(
            post("/api/admin/inquiries/{id}/answers", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(20))
        .andExpect(jsonPath("$.data.type").value("INFO"))
        .andExpect(jsonPath("$.data.title").value("답변 제목"))
        .andExpect(jsonPath("$.data.senderLabel").value("운영팀"));

    org.mockito.Mockito.verify(adminInquiryService).answer(eq(1L), eq(7L), any());
  }

  @Test
  @DisplayName("answer_title누락_400_서비스미호출")
  void answer_missingTitle_returns400() throws Exception {
    String body = "{\"type\":\"INFO\",\"content\":\"답변 내용\"}";

    mockMvc
        .perform(
            post("/api/admin/inquiries/{id}/answers", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(adminInquiryService);
  }

  @Test
  @DisplayName("answer_type누락_400_서비스미호출")
  void answer_missingType_returns400() throws Exception {
    String body = "{\"title\":\"답변 제목\",\"content\":\"답변 내용\"}";

    mockMvc
        .perform(
            post("/api/admin/inquiries/{id}/answers", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(adminInquiryService);
  }
}
