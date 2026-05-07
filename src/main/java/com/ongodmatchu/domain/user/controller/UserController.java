package com.ongodmatchu.domain.user.controller;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.quiz.dto.AttemptListItemResponse;
import com.ongodmatchu.domain.quiz.dto.MyQuizListItemResponse;
import com.ongodmatchu.domain.quiz.dto.QuizSort;
import com.ongodmatchu.domain.quiz.dto.VisibilityFilter;
import com.ongodmatchu.domain.quiz.service.QuizAttemptService;
import com.ongodmatchu.domain.quiz.service.QuizService;
import com.ongodmatchu.domain.user.dto.PasswordChangeRequest;
import com.ongodmatchu.domain.user.dto.ProfileImageUpdateRequest;
import com.ongodmatchu.domain.user.dto.ProfileStatsResponse;
import com.ongodmatchu.domain.user.dto.UserResponse;
import com.ongodmatchu.domain.user.dto.UserUpdateRequest;
import com.ongodmatchu.domain.user.service.UserService;
import com.ongodmatchu.global.response.ApiResponse;
import com.ongodmatchu.infra.s3.PresignedUrlRequest;
import com.ongodmatchu.infra.s3.PresignedUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(
    name = "User",
    description = "내 프로필 조회/수정 + 프로필 이미지 + 내가 만든 퀴즈. 응답 컨벤션은 docs/api-development.md#85-응답-값-컨벤션")
public class UserController {

  private final UserService userService;
  private final QuizService quizService;
  private final QuizAttemptService quizAttemptService;

  @Operation(summary = "내 정보 조회", description = "본인 전용. profileImageUrl 은 항상 non-null 보장")
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> getMe(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UserResponse response = userService.getMe(userDetails.getUser().getId());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(
      summary = "타 유저 프로필 조회",
      description =
          "비공개 프로필 + 외부 뷰어 → PublicUserResponse 축약 응답 (email/bio/createdAt 없음). 본인/공개는 UserResponse")
  @GetMapping("/{publicId}")
  public ResponseEntity<ApiResponse<Object>> getProfile(
      @PathVariable UUID publicId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    Long viewerId = userDetails != null ? userDetails.getUser().getId() : null;
    return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(publicId, viewerId)));
  }

  @Operation(
      summary = "내 정보 수정",
      description =
          "nickname / bio / isProfilePublic 옵셔널 (PATCH). 가능 에러: NICKNAME_ALREADY_EXISTS(409), INVALID_NICKNAME_FORMAT/INVALID_BIO_FORMAT(400)")
  @PatchMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> updateMe(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody UserUpdateRequest request) {
    UserResponse response = userService.updateMe(userDetails.getUser().getId(), request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(
      summary = "비밀번호 변경",
      description =
          "현재 비밀번호 검증 + 모든 RT 무효화 (재로그인 필요). LOCAL 만 가능. 가능 에러: INVALID_CURRENT_PASSWORD(401), OAUTH_USER_NO_PASSWORD(400), PASSWORD_POLICY_VIOLATION(400), PASSWORD_BREACHED(422)")
  @PatchMapping("/me/password")
  public ResponseEntity<ApiResponse<Void>> changePassword(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody PasswordChangeRequest request) {
    userService.changePassword(userDetails.getUser().getId(), request);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  @Operation(
      summary = "프로필 이미지 업로드 URL 발급 (presigned PUT)",
      description =
          "응답의 requiredHeaders 모든 값을 PUT 헤더에 부착해야 S3 가 허용. 자세한 흐름은 docs/api-development.md#9-s3--파일-업로드-흐름. 한도 3MB / image/jpeg|png|webp")
  @PostMapping("/me/profile-image")
  public ResponseEntity<ApiResponse<PresignedUrlResponse>> issueProfileImageUploadUrl(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody PresignedUrlRequest request) {
    PresignedUrlResponse response =
        userService.issueProfileImageUploadUrl(userDetails.getUser().getId(), request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(
      summary = "프로필 이미지 적용 (key 확정)",
      description =
          "PUT 완료 후 호출. S3 HEAD 검증 + 이전 키 best-effort 삭제. 가능 에러: INVALID_UPLOAD_KEY(400), UPLOAD_NOT_FOUND(404), UPLOAD_FORBIDDEN(403), UPLOAD_VERIFICATION_FAILED(422)")
  @PatchMapping("/me/profile-image")
  public ResponseEntity<ApiResponse<UserResponse>> applyProfileImage(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody ProfileImageUpdateRequest request) {
    UserResponse response =
        userService.applyProfileImage(userDetails.getUser().getId(), request.key());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(
      summary = "기본 이미지로 재생성",
      description = "호출마다 새 랜덤 색 SVG 생성 + 적용. 이전 키 best-effort 삭제. 매번 새 profileImageUrl 응답")
  @PostMapping("/me/profile-image/default")
  public ResponseEntity<ApiResponse<UserResponse>> regenerateDefaultProfileImage(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UserResponse response =
        userService.regenerateDefaultProfileImage(userDetails.getUser().getId());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(summary = "프로필 이미지 제거", description = "key 비움. 응답엔 서버측 default URL 폴백 (non-null 유지)")
  @DeleteMapping("/me/profile-image")
  public ResponseEntity<ApiResponse<UserResponse>> deleteProfileImage(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UserResponse response = userService.deleteProfileImage(userDetails.getUser().getId());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(
      summary = "내가 만든 퀴즈 목록",
      description =
          "visibility=ALL|PUBLIC|PRIVATE (기본 ALL), sort=latest|plays|shares|stars|comments (기본 latest). size 최대 50")
  @GetMapping("/me/quizzes")
  public ResponseEntity<ApiResponse<Page<MyQuizListItemResponse>>> getMyQuizzes(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam(required = false) String visibility,
      @RequestParam(required = false) String sort,
      @PageableDefault(size = 20) Pageable pageable) {
    Page<MyQuizListItemResponse> page =
        quizService.getMyQuizList(
            userDetails.getUser().getId(),
            VisibilityFilter.fromKey(visibility),
            QuizSort.fromKey(sort),
            pageable);
    return ResponseEntity.ok(ApiResponse.ok(page));
  }

  @Operation(
      summary = "내 프로필 통계",
      description =
          "총 퀴즈/플레이/스타/댓글/공유 합계 + 이번주 플레이 수(attempts 기반) + 평균 정답률(시도 1회 이상 퀴즈의 단순 평균, 0건이면 null)")
  @GetMapping("/me/profile/stats")
  public ResponseEntity<ApiResponse<ProfileStatsResponse>> getMyProfileStats(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    ProfileStatsResponse stats = quizService.getProfileStats(userDetails.getUser().getId());
    return ResponseEntity.ok(ApiResponse.ok(stats));
  }

  @Operation(
      summary = "타 유저의 퀴즈 목록",
      description = "외부 뷰어는 PUBLIC 만, 본인은 전체. 비공개 프로필 + 외부 뷰어 = 빈 페이지")
  @GetMapping("/{publicId}/quizzes")
  public ResponseEntity<ApiResponse<Page<MyQuizListItemResponse>>> getUserQuizzes(
      @PathVariable UUID publicId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam(required = false) String sort,
      @PageableDefault(size = 20) Pageable pageable) {
    Long viewerId = userDetails != null ? userDetails.getUser().getId() : null;
    Page<MyQuizListItemResponse> page =
        quizService.getQuizListByPublicId(publicId, viewerId, QuizSort.fromKey(sort), pageable);
    return ResponseEntity.ok(ApiResponse.ok(page));
  }

  @Operation(summary = "내 풀이 기록", description = "completedAt DESC. size 디폴트 20 / 최대 50. 인증 필수")
  @GetMapping("/me/attempts")
  public ResponseEntity<ApiResponse<Page<AttemptListItemResponse>>> getMyAttempts(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PageableDefault(size = 20) Pageable pageable) {
    Page<AttemptListItemResponse> page =
        quizAttemptService.getMyAttempts(userDetails.getUser().getId(), pageable);
    return ResponseEntity.ok(ApiResponse.ok(page));
  }

  @Operation(summary = "타 유저의 풀이 기록", description = "비공개 프로필 + 외부 뷰어 → 빈 페이지. 본인이거나 공개 프로필이면 정상 반환")
  @GetMapping("/{publicId}/attempts")
  public ResponseEntity<ApiResponse<Page<AttemptListItemResponse>>> getUserAttempts(
      @PathVariable UUID publicId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PageableDefault(size = 20) Pageable pageable) {
    Long viewerId = userDetails != null ? userDetails.getUser().getId() : null;
    Page<AttemptListItemResponse> page =
        quizAttemptService.getAttemptsByPublicId(publicId, viewerId, pageable);
    return ResponseEntity.ok(ApiResponse.ok(page));
  }
}
