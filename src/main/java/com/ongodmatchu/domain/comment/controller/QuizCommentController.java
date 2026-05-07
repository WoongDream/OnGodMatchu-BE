package com.ongodmatchu.domain.comment.controller;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.comment.dto.CommentCreateRequest;
import com.ongodmatchu.domain.comment.dto.CommentResponse;
import com.ongodmatchu.domain.comment.dto.CommentUpdateRequest;
import com.ongodmatchu.domain.comment.service.QuizCommentService;
import com.ongodmatchu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Comment", description = "퀴즈 댓글. soft delete + 본인만 수정/삭제. NFC 1~500자")
public class QuizCommentController {

  private final QuizCommentService commentService;

  @Operation(
      summary = "댓글 작성",
      description =
          "PRIVATE 퀴즈는 본인만. 가능 에러: QUIZ_NOT_FOUND(404), INVALID_COMMENT_FORMAT(400, 빈 내용/500자 초과)")
  @PostMapping("/api/quizzes/{quizId}/comments")
  public ResponseEntity<ApiResponse<CommentResponse>> create(
      @PathVariable Long quizId,
      @Valid @RequestBody CommentCreateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    CommentResponse response =
        commentService.create(userDetails.getUser().getId(), quizId, request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(
      summary = "댓글 목록 (페이지)",
      description = "최신순. soft-deleted 제외. 비로그인 허용. PRIVATE 퀴즈는 본인만 조회 가능")
  @GetMapping("/api/quizzes/{quizId}/comments")
  public ResponseEntity<ApiResponse<Page<CommentResponse>>> list(
      @PathVariable Long quizId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PageableDefault(size = 20) Pageable pageable) {
    Long viewerId = userDetails != null ? userDetails.getUser().getId() : null;
    Page<CommentResponse> page = commentService.list(viewerId, quizId, pageable);
    return ResponseEntity.ok(ApiResponse.ok(page));
  }

  @Operation(
      summary = "댓글 수정",
      description =
          "본인만. 가능 에러: COMMENT_NOT_FOUND(404, soft-deleted 포함), COMMENT_FORBIDDEN(403), INVALID_COMMENT_FORMAT(400)")
  @PatchMapping("/api/comments/{commentId}")
  public ResponseEntity<ApiResponse<CommentResponse>> update(
      @PathVariable Long commentId,
      @Valid @RequestBody CommentUpdateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    CommentResponse response =
        commentService.update(userDetails.getUser().getId(), commentId, request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(
      summary = "댓글 삭제",
      description =
          "본인만. soft delete (deleted_at 마킹). 가능 에러: COMMENT_NOT_FOUND(404), COMMENT_FORBIDDEN(403)")
  @DeleteMapping("/api/comments/{commentId}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable Long commentId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    commentService.delete(userDetails.getUser().getId(), commentId);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}
