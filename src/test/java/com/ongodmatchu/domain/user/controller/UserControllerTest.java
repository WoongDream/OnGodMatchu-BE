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
import com.ongodmatchu.domain.quiz.dto.AttemptListItemResponse;
import com.ongodmatchu.domain.quiz.dto.MyQuizListItemResponse;
import com.ongodmatchu.domain.quiz.dto.QuizSort;
import com.ongodmatchu.domain.quiz.dto.VisibilityFilter;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.quiz.service.QuizAttemptService;
import com.ongodmatchu.domain.quiz.service.QuizService;
import com.ongodmatchu.domain.user.dto.PasswordChangeRequest;
import com.ongodmatchu.domain.user.dto.ProfileImageUpdateRequest;
import com.ongodmatchu.domain.user.dto.PublicUserResponse;
import com.ongodmatchu.domain.user.dto.UserResponse;
import com.ongodmatchu.domain.user.dto.UserUpdateRequest;
import com.ongodmatchu.domain.user.dto.WithdrawRequest;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.service.UserService;
import com.ongodmatchu.infra.s3.PresignedUrlRequest;
import com.ongodmatchu.infra.s3.PresignedUrlResponse;
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

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private UserService userService;
  @MockitoBean private QuizService quizService;
  @MockitoBean private QuizAttemptService quizAttemptService;
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
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
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
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
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
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
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

  // ============ DELETE /api/users/me (회원탈퇴) ============

  @Test
  @DisplayName("withdraw_LOCAL_정상_200반환")
  void withdraw_local_returns200() throws Exception {
    WithdrawRequest request = new WithdrawRequest("currentPass");
    willDoNothing().given(userService).withdraw(eq(1L), any(WithdrawRequest.class));

    mockMvc
        .perform(
            delete("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("withdraw_OAuth_body없음_200반환")
  void withdraw_oauth_noBody_returns200() throws Exception {
    willDoNothing().given(userService).withdraw(eq(1L), any());

    mockMvc.perform(delete("/api/users/me")).andExpect(status().isOk());
  }

  // ============ POST /api/users/me/profile-image ============

  @Test
  @DisplayName("issueProfileImageUploadUrl_정상요청_200_presignedUrl반환")
  void issueProfileImageUploadUrl_validRequest_returns200() throws Exception {
    PresignedUrlRequest request = new PresignedUrlRequest("photo.jpg", "image/jpeg", 1024L);
    PresignedUrlResponse response =
        new PresignedUrlResponse(
            "https://s3.presigned/upload",
            "profile-images/uuid/photo.jpg",
            600L,
            java.util.Map.of("Content-Type", "image/jpeg", "x-amz-tagging", "status=pending"));
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
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
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

  // ============ GET /api/users/me/quizzes ============

  @Test
  @DisplayName("getMyQuizzes_인증된유저_200_Page반환")
  void getMyQuizzes_authenticated_returns200WithPage() throws Exception {
    MyQuizListItemResponse item =
        new MyQuizListItemResponse(
            10L,
            UUID.fromString("00000000-0000-0000-0000-000000000010"),
            "내 퀴즈",
            "game",
            "게임",
            QuizVisibility.PRIVATE,
            null,
            null,
            5,
            0,
            0,
            0,
            null,
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
            OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")));
    Page<MyQuizListItemResponse> page = new PageImpl<>(List.of(item));
    given(
            quizService.getMyQuizList(
                eq(1L), any(VisibilityFilter.class), any(QuizSort.class), any(Pageable.class)))
        .willReturn(page);

    mockMvc
        .perform(get("/api/users/me/quizzes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].title").value("내 퀴즈"))
        .andExpect(jsonPath("$.data.content[0].category").value("game"))
        .andExpect(jsonPath("$.data.content[0].categoryLabel").value("게임"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  @DisplayName("getMyQuizzes_퀴즈없음_빈페이지반환_200")
  void getMyQuizzes_noQuizzes_returnsEmptyPage() throws Exception {
    given(
            quizService.getMyQuizList(
                eq(1L), any(VisibilityFilter.class), any(QuizSort.class), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/api/users/me/quizzes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  // ============ GET /api/users/{publicId}/quizzes ============

  @Test
  @DisplayName("getUserQuizzes_공개프로필_비로그인뷰어_200_정상목록반환")
  void getUserQuizzes_publicProfile_anonymousViewer_returns200() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    MyQuizListItemResponse item =
        new MyQuizListItemResponse(
            11L,
            UUID.fromString("00000000-0000-0000-0000-000000000011"),
            "공개 퀴즈",
            "music",
            "음악",
            QuizVisibility.PUBLIC,
            null,
            null,
            10,
            0,
            0,
            0,
            null,
            OffsetDateTime.of(2024, 6, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
            OffsetDateTime.of(2024, 6, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")));
    Page<MyQuizListItemResponse> page = new PageImpl<>(List.of(item));
    given(
            quizService.getQuizListByPublicId(
                eq(targetPublicId), eq(null), any(QuizSort.class), any(Pageable.class)))
        .willReturn(page);

    SecurityContextHolder.clearContext();

    mockMvc
        .perform(get("/api/users/{publicId}/quizzes", targetPublicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].title").value("공개 퀴즈"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  @DisplayName("getUserQuizzes_비공개프로필_외부뷰어_200_빈페이지반환")
  void getUserQuizzes_privateProfile_externalViewer_returnsEmptyPage() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000004");
    given(
            quizService.getQuizListByPublicId(
                eq(targetPublicId), eq(1L), any(QuizSort.class), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/api/users/{publicId}/quizzes", targetPublicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  @DisplayName("getUserQuizzes_로그인뷰어_viewerUserId전달_200반환")
  void getUserQuizzes_authenticatedViewer_passesViewerId() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000005");
    MyQuizListItemResponse item =
        new MyQuizListItemResponse(
            12L,
            UUID.fromString("00000000-0000-0000-0000-000000000012"),
            "타인퀴즈",
            "etc",
            "기타",
            QuizVisibility.PUBLIC,
            null,
            null,
            3,
            0,
            0,
            0,
            null,
            OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")),
            OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.of("+09:00")));
    Page<MyQuizListItemResponse> page = new PageImpl<>(List.of(item));
    given(
            quizService.getQuizListByPublicId(
                eq(targetPublicId), eq(1L), any(QuizSort.class), any(Pageable.class)))
        .willReturn(page);

    mockMvc
        .perform(get("/api/users/{publicId}/quizzes", targetPublicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].title").value("타인퀴즈"));
  }

  // ============ GET /api/users/me/attempts ============

  @Test
  @DisplayName("getMyAttempts_인증된유저_200_Page반환")
  void getMyAttempts_authenticated_returns200WithPage() throws Exception {
    AttemptListItemResponse item =
        new AttemptListItemResponse(
            100L,
            1L,
            UUID.fromString("00000000-0000-0000-0000-000000000010"),
            "게임 퀴즈",
            "game",
            "게임",
            null,
            null,
            4,
            5,
            80.0,
            OffsetDateTime.of(2025, 5, 1, 12, 0, 0, 0, ZoneOffset.of("+09:00")));
    Page<AttemptListItemResponse> page = new PageImpl<>(List.of(item));
    given(quizAttemptService.getMyAttempts(eq(1L), any(Pageable.class))).willReturn(page);

    mockMvc
        .perform(get("/api/users/me/attempts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value(100))
        .andExpect(jsonPath("$.data.content[0].quizTitle").value("게임 퀴즈"))
        .andExpect(jsonPath("$.data.content[0].score").value(4))
        .andExpect(jsonPath("$.data.content[0].totalQuestions").value(5))
        .andExpect(jsonPath("$.data.content[0].percent").value(80.0))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  @DisplayName("getMyAttempts_풀이기록없음_200_빈페이지반환")
  void getMyAttempts_noAttempts_returnsEmptyPage() throws Exception {
    given(quizAttemptService.getMyAttempts(eq(1L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/api/users/me/attempts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  // ============ GET /api/users/{publicId}/attempts ============

  @Test
  @DisplayName("getUserAttempts_공개프로필_비로그인뷰어_200_정상반환")
  void getUserAttempts_publicProfile_anonymousViewer_returns200() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000006");
    AttemptListItemResponse item =
        new AttemptListItemResponse(
            200L,
            2L,
            UUID.fromString("00000000-0000-0000-0000-000000000020"),
            "음악 퀴즈",
            "music",
            "음악",
            null,
            null,
            3,
            5,
            60.0,
            OffsetDateTime.of(2025, 4, 1, 10, 0, 0, 0, ZoneOffset.of("+09:00")));
    Page<AttemptListItemResponse> page = new PageImpl<>(List.of(item));
    given(
            quizAttemptService.getAttemptsByPublicId(
                eq(targetPublicId), eq(null), any(Pageable.class)))
        .willReturn(page);

    SecurityContextHolder.clearContext();

    mockMvc
        .perform(get("/api/users/{publicId}/attempts", targetPublicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].id").value(200))
        .andExpect(jsonPath("$.data.content[0].quizTitle").value("음악 퀴즈"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  @DisplayName("getUserAttempts_비공개프로필_외부뷰어_200_빈페이지반환")
  void getUserAttempts_privateProfile_externalViewer_returnsEmptyPage() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000007");
    given(quizAttemptService.getAttemptsByPublicId(eq(targetPublicId), eq(1L), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/api/users/{publicId}/attempts", targetPublicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  @DisplayName("getUserAttempts_로그인뷰어_viewerUserId전달_200반환")
  void getUserAttempts_authenticatedViewer_passesViewerId() throws Exception {
    UUID targetPublicId = UUID.fromString("00000000-0000-0000-0000-000000000008");
    AttemptListItemResponse item =
        new AttemptListItemResponse(
            300L,
            3L,
            UUID.fromString("00000000-0000-0000-0000-000000000030"),
            "애니 퀴즈",
            "anime",
            "애니메이션",
            null,
            null,
            5,
            5,
            100.0,
            OffsetDateTime.of(2025, 3, 15, 9, 0, 0, 0, ZoneOffset.of("+09:00")));
    Page<AttemptListItemResponse> page = new PageImpl<>(List.of(item));
    given(quizAttemptService.getAttemptsByPublicId(eq(targetPublicId), eq(1L), any(Pageable.class)))
        .willReturn(page);

    mockMvc
        .perform(get("/api/users/{publicId}/attempts", targetPublicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].quizTitle").value("애니 퀴즈"))
        .andExpect(jsonPath("$.data.content[0].percent").value(100.0));
  }
}
