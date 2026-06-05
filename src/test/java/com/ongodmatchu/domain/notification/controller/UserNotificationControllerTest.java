package com.ongodmatchu.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.notification.dto.NotificationResponse;
import com.ongodmatchu.domain.notification.service.UserNotificationService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserNotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserNotificationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserNotificationService userNotificationService;
  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  @BeforeEach
  void setUp() {
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

  @Test
  @DisplayName("getPending_정상_200_본인id로위임_목록반환")
  void getPending_returns200() throws Exception {
    NotificationResponse n =
        new NotificationResponse(
            10L,
            "INFO",
            "안내",
            "제목",
            "내용",
            "운영팀",
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")));
    given(userNotificationService.getPending(eq(1L))).willReturn(List.of(n));

    mockMvc
        .perform(get("/api/users/me/notifications/pending"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value(10))
        .andExpect(jsonPath("$.data[0].type").value("INFO"))
        .andExpect(jsonPath("$.data[0].senderLabel").value("운영팀"));

    org.mockito.Mockito.verify(userNotificationService).getPending(eq(1L));
  }

  @Test
  @DisplayName("getReceived_정상_200_본인id로위임_페이지응답")
  void getReceived_returns200() throws Exception {
    NotificationResponse n =
        new NotificationResponse(
            10L,
            "INFO",
            "안내",
            "제목",
            "내용",
            "운영팀",
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")));
    Page<NotificationResponse> page = new PageImpl<>(List.of(n));
    given(userNotificationService.getReceived(eq(1L), any(Pageable.class))).willReturn(page);

    mockMvc
        .perform(get("/api/users/me/notifications"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value(10))
        .andExpect(jsonPath("$.data.content[0].type").value("INFO"))
        .andExpect(jsonPath("$.data.content[0].senderLabel").value("운영팀"))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    org.mockito.Mockito.verify(userNotificationService).getReceived(eq(1L), any(Pageable.class));
  }

  @Test
  @DisplayName("markRead_정상_200_본인id/알림id로위임")
  void markRead_returns200() throws Exception {
    mockMvc
        .perform(post("/api/users/me/notifications/{id}/read", 10L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    org.mockito.Mockito.verify(userNotificationService).markRead(eq(1L), eq(10L));
  }
}
