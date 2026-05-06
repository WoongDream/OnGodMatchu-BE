package com.ongodmatchu.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.user.dto.PasswordChangeRequest;
import com.ongodmatchu.domain.user.dto.ProfileImageUpdateRequest;
import com.ongodmatchu.domain.user.dto.PublicUserResponse;
import com.ongodmatchu.domain.user.dto.UserResponse;
import com.ongodmatchu.domain.user.dto.UserUpdateRequest;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.service.UserService;
import com.ongodmatchu.infra.s3.PresignedUrlRequest;
import com.ongodmatchu.infra.s3.PresignedUrlResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private UserService userService;
  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  private User testUser;
  private UserResponse sampleUserResponse;

  @BeforeEach
  void setUp() {
    testUser =
        User.builder()
            .email("user@example.com")
            .nickname("테스트유저")
            .password("hashed")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(testUser, "id", 1L);
    ReflectionTestUtils.setField(
        testUser, "publicId", UUID.fromString("00000000-0000-0000-0000-000000000001"));

    CustomUserDetails userDetails = new CustomUserDetails(testUser);
    Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);

    sampleUserResponse =
        new UserResponse(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "테스트유저",
            "user@example.com",
            "https://cdn.example.com/default.png",
            "안녕하세요",
            LocalDateTime.of(2024, 1, 1, 0, 0),
            100L,
            true,
            "LOCAL");
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ============ GET /api/users/me ============

  @Test
  @DisplayName("getMe_정상요청_200_UserResponse반환")
  void getMe_authenticated_returns200WithUserResponse() throws Exception {
    given(userService.getMe(1L)).willReturn(sampleUserResponse);

    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value("00000000-0000-0000-0000-000000000001"))
        .andExpect(jsonPath("$.data.nickname").value("테스트유저"))
        .andExpect(jsonPath("$.data.email").value("user@example.com"))
        .andExpect(jsonPath("$.data.profileImageUrl").value("https://cdn.example.com/default.png"))
        .andExpect(jsonPath("$.data.bio").value("안녕하세요"))
        .andExpect(jsonPath("$.data.activeDays").value(100))
        .andExpect(jsonPath("$.data.isProfilePublic").value(true))
        .andExpect(jsonPath("$.data.provider").value("LOCAL"));
  }

  // ============ GET /api/users/{publicId} ============

  @Test
  @DisplayName("getProfile_공개프로필_로그인뷰어_200_UserResponse반환")
  void getProfile_publicProfile_authenticatedViewer_returns200WithUserResponse() throws Exception {
    UUID publicId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    given(userService.getProfile(eq(publicId), eq(1L))).willReturn(sampleUserResponse);

    mockMvc
        .perform(get("/api/users/{publicId}", publicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.email").value("user@example.com"))
        .andExpect(jsonPath("$.data.bio").value("안녕하세요"))
        .andExpect(jsonPath("$.data.createdAt").exists());
  }

  @Test
  @DisplayName("getProfile_비공개프로필_외부뷰어_200_PublicUserResponse반환_email없음")
  void getProfile_privateProfile_externalViewer_returns200WithPublicUserResponse()
      throws Exception {
    UUID publicId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    PublicUserResponse publicResponse =
        new PublicUserResponse(publicId, "비공개유저", "https://cdn.example.com/default.png", false);
    given(userService.getProfile(eq(publicId), eq(1L))).willReturn(publicResponse);

    mockMvc
        .perform(get("/api/users/{publicId}", publicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.nickname").value("비공개유저"))
        .andExpect(jsonPath("$.data.isProfilePublic").value(false))
        .andExpect(jsonPath("$.data.email").doesNotExist())
        .andExpect(jsonPath("$.data.bio").doesNotExist())
        .andExpect(jsonPath("$.data.createdAt").doesNotExist());
  }

  @Test
  @DisplayName("getProfile_비로그인뷰어_viewerUserId_null로전달_200반환")
  void getProfile_anonymousViewer_passesNullViewerId() throws Exception {
    UUID publicId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    UserResponse anonResponse =
        new UserResponse(
            publicId,
            "공개유저",
            "anon@example.com",
            "https://cdn.example.com/default.png",
            null,
            LocalDateTime.of(2024, 1, 1, 0, 0),
            10L,
            true,
            "LOCAL");
    given(userService.getProfile(eq(publicId), eq(null))).willReturn(anonResponse);

    // SecurityContext 비워서 비로그인 시뮬레이션
    SecurityContextHolder.clearContext();

    mockMvc
        .perform(get("/api/users/{publicId}", publicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  // ============ PATCH /api/users/me ============

  @Test
  @DisplayName("updateMe_정상요청_200_UserResponse반환")
  void updateMe_validRequest_returns200() throws Exception {
    UserUpdateRequest request = new UserUpdateRequest("새닉네임", "새한줄소개", true);
    UserResponse updated =
        new UserResponse(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "새닉네임",
            "user@example.com",
            "https://cdn.example.com/default.png",
            "새한줄소개",
            LocalDateTime.of(2024, 1, 1, 0, 0),
            100L,
            true,
            "LOCAL");
    given(userService.updateMe(eq(1L), any(UserUpdateRequest.class))).willReturn(updated);

    mockMvc
        .perform(
            patch("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
        .andExpect(jsonPath("$.data.bio").value("새한줄소개"));
  }

  @Test
  @DisplayName("updateMe_닉네임길이위반_400반환")
  void updateMe_nicknameTooShort_returns400() throws Exception {
    String body = "{\"nickname\":\"a\"}";

    mockMvc
        .perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  // ============ PATCH /api/users/me/password ============

  @Test
  @DisplayName("changePassword_정상요청_200반환")
  void changePassword_validRequest_returns200() throws Exception {
    PasswordChangeRequest request = new PasswordChangeRequest("currentPass", "newPassword123!");
    willDoNothing().given(userService).changePassword(eq(1L), any(PasswordChangeRequest.class));

    mockMvc
        .perform(
            patch("/api/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("changePassword_currentPassword빈값_400반환")
  void changePassword_blankCurrentPassword_returns400() throws Exception {
    String body = "{\"currentPassword\":\"\",\"newPassword\":\"newPassword123!\"}";

    mockMvc
        .perform(
            patch("/api/users/me/password").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @DisplayName("changePassword_newPassword빈값_400반환")
  void changePassword_blankNewPassword_returns400() throws Exception {
    String body = "{\"currentPassword\":\"currentPass\",\"newPassword\":\"\"}";

    mockMvc
        .perform(
            patch("/api/users/me/password").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  // ============ POST /api/users/me/profile-image ============

  @Test
  @DisplayName("issueProfileImageUploadUrl_정상요청_200_presignedUrl반환")
  void issueProfileImageUploadUrl_validRequest_returns200() throws Exception {
    PresignedUrlRequest request = new PresignedUrlRequest("photo.jpg", "image/jpeg", 1024L);
    PresignedUrlResponse response =
        new PresignedUrlResponse(
            "https://s3.presigned/upload", "profile-images/uuid/photo.jpg", 600L);
    given(userService.issueProfileImageUploadUrl(eq(1L), any(PresignedUrlRequest.class)))
        .willReturn(response);

    mockMvc
        .perform(
            post("/api/users/me/profile-image")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.uploadUrl").value("https://s3.presigned/upload"))
        .andExpect(jsonPath("$.data.key").value("profile-images/uuid/photo.jpg"));
  }

  @Test
  @DisplayName("issueProfileImageUploadUrl_filename빈값_400반환")
  void issueProfileImageUploadUrl_blankFilename_returns400() throws Exception {
    String body = "{\"filename\":\"\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1024}";

    mockMvc
        .perform(
            post("/api/users/me/profile-image")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  // ============ PATCH /api/users/me/profile-image ============

  @Test
  @DisplayName("applyProfileImage_정상요청_200_UserResponse반환")
  void applyProfileImage_validRequest_returns200() throws Exception {
    ProfileImageUpdateRequest request =
        new ProfileImageUpdateRequest("profile-images/uuid/photo.jpg");
    given(userService.applyProfileImage(eq(1L), anyString())).willReturn(sampleUserResponse);

    mockMvc
        .perform(
            patch("/api/users/me/profile-image")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.nickname").value("테스트유저"));
  }

  @Test
  @DisplayName("applyProfileImage_key빈값_400반환")
  void applyProfileImage_blankKey_returns400() throws Exception {
    String body = "{\"key\":\"\"}";

    mockMvc
        .perform(
            patch("/api/users/me/profile-image")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  // ============ DELETE /api/users/me/profile-image ============

  @Test
  @DisplayName("deleteProfileImage_정상요청_200_UserResponse반환")
  void deleteProfileImage_validRequest_returns200() throws Exception {
    UserResponse responseAfterDelete =
        new UserResponse(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "테스트유저",
            "user@example.com",
            "https://cdn.example.com/default.png",
            null,
            LocalDateTime.of(2024, 1, 1, 0, 0),
            100L,
            true,
            "LOCAL");
    given(userService.deleteProfileImage(1L)).willReturn(responseAfterDelete);

    mockMvc
        .perform(delete("/api/users/me/profile-image"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.profileImageUrl").value("https://cdn.example.com/default.png"));
  }
}
