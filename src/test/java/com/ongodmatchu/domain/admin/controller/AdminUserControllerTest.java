package com.ongodmatchu.domain.admin.controller;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongodmatchu.domain.admin.dto.AdminUserDetailResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserHistoryResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserSummaryResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserUpdateRequest;
import com.ongodmatchu.domain.admin.dto.MonthlyUserStatResponse;
import com.ongodmatchu.domain.admin.service.AdminUserService;
import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.Role;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.entity.UserStatus;
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

@WebMvcTest(AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminUserControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AdminUserService adminUserService;
  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  private AdminUserResponse sampleResponse;

  @BeforeEach
  void setUp() {
    // actor(OWNER) 를 @AuthenticationPrincipal 로 주입 — 컨트롤러가 actor.getUser().getId() 를 꺼낸다.
    User actor =
        User.builder()
            .email("owner@example.com")
            .nickname("운영자")
            .password("hashed")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(actor, "id", 1L);
    ReflectionTestUtils.setField(
        actor, "publicId", UUID.fromString("00000000-0000-0000-0000-000000000001"));
    ReflectionTestUtils.setField(actor, "role", Role.OWNER);
    ReflectionTestUtils.setField(actor, "termsVersion", "1.0");
    ReflectionTestUtils.setField(actor, "privacyVersion", "1.0");

    CustomUserDetails actorDetails = new CustomUserDetails(actor);
    Authentication auth = new UsernamePasswordAuthenticationToken(actorDetails, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);

    sampleResponse =
        new AdminUserResponse(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            "대상유저",
            "target@example.com",
            null,
            "USER",
            "ACTIVE",
            null,
            "LOCAL",
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
            null);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ============ GET /api/admin/users ============

  @Test
  @DisplayName("getUsers_필터없음_200_Page반환_status는null로위임")
  void getUsers_noFilter_returns200AndPassesNullStatus() throws Exception {
    Page<AdminUserResponse> page = new PageImpl<>(List.of(sampleResponse));
    given(adminUserService.getUsers(isNull(), isNull(), isNull(), any(Pageable.class)))
        .willReturn(page);

    mockMvc
        .perform(get("/api/admin/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].nickname").value("대상유저"))
        .andExpect(jsonPath("$.data.content[0].email").value("target@example.com"))
        .andExpect(jsonPath("$.data.content[0].role").value("USER"))
        .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    org.mockito.Mockito.verify(adminUserService)
        .getUsers(isNull(), isNull(), isNull(), any(Pageable.class));
  }

  @Test
  @DisplayName("getUsers_status지정_enum.name()문자열로_role/query와함께_위임")
  void getUsers_withStatus_passesEnumNameAsString() throws Exception {
    given(
            adminUserService.getUsers(
                eq(UserStatus.SUSPENDED.name()), eq(Role.ADMIN), eq("kim"), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/api/admin/users")
                .param("status", "SUSPENDED")
                .param("role", "ADMIN")
                .param("query", "kim"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(adminUserService)
        .getUsers(
            statusCaptor.capture(),
            roleCaptor.capture(),
            queryCaptor.capture(),
            any(Pageable.class));
    assertThat(statusCaptor.getValue()).isEqualTo("SUSPENDED");
    assertThat(roleCaptor.getValue()).isEqualTo(Role.ADMIN);
    assertThat(queryCaptor.getValue()).isEqualTo("kim");
  }

  // ============ POST /api/admin/users/{publicId}/suspend ============

  @Test
  @DisplayName("suspend_정상요청_200_actorId/publicId/days_정확히위임")
  void suspend_validRequest_returns200AndDelegates() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    String body = "{\"days\":7}";
    given(adminUserService.suspend(eq(1L), eq(targetPublicId), eq(7))).willReturn(sampleResponse);

    mockMvc
        .perform(
            post("/api/admin/users/{publicId}/suspend", targetPublicId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.nickname").value("대상유저"));

    org.mockito.Mockito.verify(adminUserService).suspend(eq(1L), eq(targetPublicId), eq(7));
  }

  @Test
  @DisplayName("suspend_days0_400반환_서비스미호출")
  void suspend_zeroDays_returns400() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    String body = "{\"days\":0}";

    mockMvc
        .perform(
            post("/api/admin/users/{publicId}/suspend", targetPublicId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(adminUserService);
  }

  @Test
  @DisplayName("suspend_days음수_400반환")
  void suspend_negativeDays_returns400() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    String body = "{\"days\":-5}";

    mockMvc
        .perform(
            post("/api/admin/users/{publicId}/suspend", targetPublicId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(adminUserService);
  }

  // ============ POST /api/admin/users/{publicId}/unsuspend ============

  @Test
  @DisplayName("unsuspend_정상요청_200_actorId/publicId_위임")
  void unsuspend_validRequest_returns200AndDelegates() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    given(adminUserService.unsuspend(eq(1L), eq(targetPublicId))).willReturn(sampleResponse);

    mockMvc
        .perform(post("/api/admin/users/{publicId}/unsuspend", targetPublicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));

    org.mockito.Mockito.verify(adminUserService).unsuspend(eq(1L), eq(targetPublicId));
  }

  // ============ PATCH /api/admin/users/{publicId}/role ============

  @Test
  @DisplayName("changeRole_정상요청_200_actorId/publicId/role_정확히위임")
  void changeRole_validRequest_returns200AndDelegates() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    String body = "{\"role\":\"ADMIN\"}";
    AdminUserResponse promoted =
        new AdminUserResponse(
            targetPublicId,
            "대상유저",
            "target@example.com",
            null,
            "ADMIN",
            "ACTIVE",
            null,
            "LOCAL",
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
            null);
    given(adminUserService.changeRole(eq(1L), eq(targetPublicId), eq(Role.ADMIN)))
        .willReturn(promoted);

    mockMvc
        .perform(
            patch("/api/admin/users/{publicId}/role", targetPublicId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.role").value("ADMIN"));

    org.mockito.Mockito.verify(adminUserService)
        .changeRole(eq(1L), eq(targetPublicId), eq(Role.ADMIN));
  }

  @Test
  @DisplayName("changeRole_role누락_400반환_서비스미호출")
  void changeRole_missingRole_returns400() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    String body = "{}";

    mockMvc
        .perform(
            patch("/api/admin/users/{publicId}/role", targetPublicId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(adminUserService);
  }

  // ============ GET /api/admin/users/{publicId} ============

  @Test
  @DisplayName("getUserDetail_정상_200_상세필드반환")
  void getUserDetail_returns200() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    AdminUserDetailResponse detail =
        new AdminUserDetailResponse(
            targetPublicId,
            "대상유저",
            "target@example.com",
            "https://img/profile.svg",
            "소개",
            "USER",
            "ACTIVE",
            null,
            "LOCAL",
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
            OffsetDateTime.of(2024, 6, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
            7L,
            82.5,
            3L,
            120L,
            45L);
    given(adminUserService.getUserDetail(targetPublicId)).willReturn(detail);

    mockMvc
        .perform(get("/api/admin/users/{publicId}", targetPublicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.nickname").value("대상유저"))
        .andExpect(jsonPath("$.data.withdrawnAt").exists())
        .andExpect(jsonPath("$.data.solvedCount").value(7))
        .andExpect(jsonPath("$.data.quizCount").value(3));

    org.mockito.Mockito.verify(adminUserService).getUserDetail(targetPublicId);
  }

  // ============ PATCH /api/admin/users/{publicId} ============

  @Test
  @DisplayName("updateUser_정상_200_actorId/publicId_위임")
  void updateUser_returns200AndDelegates() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    String body = "{\"role\":\"ADMIN\",\"resetBio\":true}";
    given(
            adminUserService.updateUser(
                eq(1L), eq(targetPublicId), any(AdminUserUpdateRequest.class)))
        .willReturn(sampleResponse);

    mockMvc
        .perform(
            patch("/api/admin/users/{publicId}", targetPublicId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.nickname").value("대상유저"));

    org.mockito.Mockito.verify(adminUserService)
        .updateUser(eq(1L), eq(targetPublicId), any(AdminUserUpdateRequest.class));
  }

  // ============ POST /api/admin/users/{publicId}/notifications ============

  @Test
  @DisplayName("sendNotification_정상_200_위임")
  void sendNotification_returns200() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    String body = "{\"type\":\"INFO\",\"title\":\"제목\",\"content\":\"내용\"}";
    given(adminUserService.sendNotification(eq(1L), eq(targetPublicId), any()))
        .willReturn(sampleResponse);

    mockMvc
        .perform(
            post("/api/admin/users/{publicId}/notifications", targetPublicId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    org.mockito.Mockito.verify(adminUserService)
        .sendNotification(eq(1L), eq(targetPublicId), any());
  }

  @Test
  @DisplayName("sendNotification_title누락_400_서비스미호출")
  void sendNotification_missingTitle_returns400() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    String body = "{\"type\":\"INFO\",\"content\":\"내용\"}";

    mockMvc
        .perform(
            post("/api/admin/users/{publicId}/notifications", targetPublicId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));

    org.mockito.Mockito.verifyNoInteractions(adminUserService);
  }

  // ============ GET /api/admin/users/{publicId}/histories ============

  @Test
  @DisplayName("getHistories_정상_200_Page반환")
  void getHistories_returns200() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    AdminUserHistoryResponse history =
        new AdminUserHistoryResponse(
            1L,
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "운영자",
            "OWNER",
            "ROLE_CHANGE",
            "역할 변경",
            "USER → ADMIN",
            null,
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")));
    given(adminUserService.getHistories(eq(targetPublicId), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(history)));

    mockMvc
        .perform(get("/api/admin/users/{publicId}/histories", targetPublicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].changeType").value("ROLE_CHANGE"))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    org.mockito.Mockito.verify(adminUserService)
        .getHistories(eq(targetPublicId), any(Pageable.class));
  }

  // ============ GET /api/admin/users/stats/summary ============

  @Test
  @DisplayName("getSummary_정상_200_통계반환")
  void getSummary_returns200() throws Exception {
    given(adminUserService.getSummary())
        .willReturn(new AdminUserSummaryResponse(100L, 1L, 4L, 95L, 3L, 12L));

    mockMvc
        .perform(get("/api/admin/users/stats/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalUsers").value(100))
        .andExpect(jsonPath("$.data.suspendedCount").value(3))
        .andExpect(jsonPath("$.data.newThisMonth").value(12));

    org.mockito.Mockito.verify(adminUserService).getSummary();
  }

  // ============ GET /api/admin/users/stats/monthly ============

  @Test
  @DisplayName("getMonthlyStats_months지정_200_리스트반환_파라미터위임")
  void getMonthlyStats_returns200AndDelegatesMonths() throws Exception {
    given(adminUserService.getMonthlyStats(3))
        .willReturn(List.of(new MonthlyUserStatResponse("2024-04", 90L, 5L, 1L)));

    mockMvc
        .perform(get("/api/admin/users/stats/monthly").param("months", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].yearMonth").value("2024-04"))
        .andExpect(jsonPath("$.data[0].cumulative").value(90));

    org.mockito.Mockito.verify(adminUserService).getMonthlyStats(3);
  }
}
