package com.ongodmatchu.domain.comment.controller;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.comment.dto.CommentCreateRequest;
import com.ongodmatchu.domain.comment.dto.CommentResponse;
import com.ongodmatchu.domain.comment.dto.CommentUpdateRequest;
import com.ongodmatchu.domain.comment.service.QuizCommentService;
import com.ongodmatchu.global.response.ApiResponse;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QuizCommentController {

  private final QuizCommentService commentService;

  @PostMapping("/api/quizzes/{quizId}/comments")
  public ResponseEntity<ApiResponse<CommentResponse>> create(
      @PathVariable Long quizId,
      @Valid @RequestBody CommentCreateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    CommentResponse response =
        commentService.create(userDetails.getUser().getId(), quizId, request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/api/quizzes/{quizId}/comments")
  public ResponseEntity<ApiResponse<Page<CommentResponse>>> list(
      @PathVariable Long quizId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PageableDefault(size = 20) Pageable pageable) {
    Long viewerId = userDetails != null ? userDetails.getUser().getId() : null;
    Page<CommentResponse> page = commentService.list(viewerId, quizId, pageable);
    return ResponseEntity.ok(ApiResponse.ok(page));
  }

  @PatchMapping("/api/comments/{commentId}")
  public ResponseEntity<ApiResponse<CommentResponse>> update(
      @PathVariable Long commentId,
      @Valid @RequestBody CommentUpdateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    CommentResponse response =
        commentService.update(userDetails.getUser().getId(), commentId, request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @DeleteMapping("/api/comments/{commentId}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable Long commentId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    commentService.delete(userDetails.getUser().getId(), commentId);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}
