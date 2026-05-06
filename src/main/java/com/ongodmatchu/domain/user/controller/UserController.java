package com.ongodmatchu.domain.user.controller;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.quiz.dto.QuizResponse;
import com.ongodmatchu.domain.quiz.service.QuizService;
import com.ongodmatchu.domain.user.dto.PasswordChangeRequest;
import com.ongodmatchu.domain.user.dto.ProfileImageUpdateRequest;
import com.ongodmatchu.domain.user.dto.UserResponse;
import com.ongodmatchu.domain.user.dto.UserUpdateRequest;
import com.ongodmatchu.domain.user.service.UserService;
import com.ongodmatchu.global.response.ApiResponse;
import com.ongodmatchu.infra.s3.PresignedUrlRequest;
import com.ongodmatchu.infra.s3.PresignedUrlResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final QuizService quizService;

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> getMe(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UserResponse response = userService.getMe(userDetails.getUser().getId());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/{publicId}")
  public ResponseEntity<ApiResponse<Object>> getProfile(
      @PathVariable UUID publicId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    Long viewerId = userDetails != null ? userDetails.getUser().getId() : null;
    return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(publicId, viewerId)));
  }

  @PatchMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> updateMe(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody UserUpdateRequest request) {
    UserResponse response = userService.updateMe(userDetails.getUser().getId(), request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @PatchMapping("/me/password")
  public ResponseEntity<ApiResponse<Void>> changePassword(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody PasswordChangeRequest request) {
    userService.changePassword(userDetails.getUser().getId(), request);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  @PostMapping("/me/profile-image")
  public ResponseEntity<ApiResponse<PresignedUrlResponse>> issueProfileImageUploadUrl(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody PresignedUrlRequest request) {
    PresignedUrlResponse response =
        userService.issueProfileImageUploadUrl(userDetails.getUser().getId(), request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @PatchMapping("/me/profile-image")
  public ResponseEntity<ApiResponse<UserResponse>> applyProfileImage(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody ProfileImageUpdateRequest request) {
    UserResponse response =
        userService.applyProfileImage(userDetails.getUser().getId(), request.key());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @PostMapping("/me/profile-image/default")
  public ResponseEntity<ApiResponse<UserResponse>> regenerateDefaultProfileImage(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UserResponse response =
        userService.regenerateDefaultProfileImage(userDetails.getUser().getId());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @DeleteMapping("/me/profile-image")
  public ResponseEntity<ApiResponse<UserResponse>> deleteProfileImage(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UserResponse response = userService.deleteProfileImage(userDetails.getUser().getId());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/me/quizzes")
  public ResponseEntity<ApiResponse<Page<QuizResponse>>> getMyQuizzes(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<QuizResponse> page = quizService.getMyQuizList(userDetails.getUser().getId(), pageable);
    return ResponseEntity.ok(ApiResponse.ok(page));
  }

  @GetMapping("/{publicId}/quizzes")
  public ResponseEntity<ApiResponse<Page<QuizResponse>>> getUserQuizzes(
      @PathVariable UUID publicId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Long viewerId = userDetails != null ? userDetails.getUser().getId() : null;
    Page<QuizResponse> page = quizService.getQuizListByPublicId(publicId, viewerId, pageable);
    return ResponseEntity.ok(ApiResponse.ok(page));
  }
}
