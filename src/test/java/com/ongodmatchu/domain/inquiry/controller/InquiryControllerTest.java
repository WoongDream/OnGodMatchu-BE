package com.ongodmatchu.domain.inquiry.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.inquiry.dto.InquiryCreateRequest;
import com.ongodmatchu.domain.inquiry.dto.InquiryResponse;
import com.ongodmatchu.domain.inquiry.service.InquiryService;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.Role;
import com.ongodmatchu.domain.user.entity.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(InquiryController.class)
@AutoConfigureMockMvc(addFilters = false)
class InquiryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InquiryService inquiryService;
  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  @BeforeEach
  void setUp() {
    // me 를 @AuthenticationPrincipal 로 주입 — 컨트롤러가 me.getUser().getId() 를 꺼낸다.
    User me =
        User.builder()
            .email("me@example.com")
            .nickname("나")
            .password("hashed")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(me, "id", 1L);
    ReflectionTestUtils.setField(me, "publicId", UUID.randomUUID());
    ReflectionTestUtils.setField(me, "role", Role.USER);
    ReflectionTestUtils.setField(me, "termsVersion", "1.0");
    ReflectionTestUtils.setField(me, "privacyVersion", "1.0");

    CustomUserDetails meDetails = new CustomUserDetails(me);
    Authentication auth = new UsernamePasswordAuthenticationToken(meDetails, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private InquiryResponse sampleResponse() {
    return new InquiryResponse(
        7L,
        "결제 문의",
        "환불이 안돼요",
        "PENDING",
        "대기",
        OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
        List.of());
  }

  // ============ POST /api/inquiries ============

  @Test
  @DisplayName("create_정상요청_200_현재사용자id/request위임")
  void create_validRequest_returns200AndDelegates() throws Exception {
    String body = "{\"title\":\"결제 문의\",\"content\":\"환불이 안돼요\"}";
    given(inquiryService.create(eq(1L), any(InquiryCreateRequest.class)))
        .willReturn(sampleResponse());

    mockMvc
        .perform(post("/api/inquiries").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(7))
        .andExpect(jsonPath("$.data.title").value("결제 문의"))
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.statusLabel").value("대기"))
        .andExpect(jsonPath("$.data.answers").isArray());

    org.mockito.Mockito.verify(inquiryService).create(eq(1L), any(InquiryCreateRequest.class));
  }

  @Test
  @DisplayName("create_title공백_400_서비스미호출")
  void create_blankTitle_returns400() throws Exception {
    String body = "{\"title\":\"\",\"content\":\"내용\"}";

    mockMvc
        .perform(post("/api/inquiries").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(inquiryService);
  }

  @Test
  @DisplayName("create_title누락_400_서비스미호출")
  void create_missingTitle_returns400() throws Exception {
    String body = "{\"content\":\"내용\"}";

    mockMvc
        .perform(post("/api/inquiries").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(inquiryService);
  }

  @Test
  @DisplayName("create_content1000자초과_400_서비스미호출")
  void create_contentTooLong_returns400() throws Exception {
    String tooLong = "가".repeat(1001);
    String body = "{\"title\":\"제목\",\"content\":\"" + tooLong + "\"}";

    mockMvc
        .perform(post("/api/inquiries").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(inquiryService);
  }

  // ============ GET /api/users/me/inquiries ============

  @Test
  @DisplayName("getMyInquiries_정상_200_본인id로위임_Page매핑")
  void getMyInquiries_returns200AndDelegatesOwnId() throws Exception {
    Page<InquiryResponse> page = new PageImpl<>(List.of(sampleResponse()));
    given(inquiryService.getMyInquiries(eq(1L), any(Pageable.class))).willReturn(page);

    mockMvc
        .perform(get("/api/users/me/inquiries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value(7))
        .andExpect(jsonPath("$.data.content[0].title").value("결제 문의"))
        .andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    org.mockito.Mockito.verify(inquiryService).getMyInquiries(eq(1L), any(Pageable.class));
  }
}
