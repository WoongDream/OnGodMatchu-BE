package com.ongodmatchu.domain.quiz.controller;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.quiz.dto.CategoryResponse;
import com.ongodmatchu.domain.quiz.dto.QuizCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuizDetailResponse;
import com.ongodmatchu.domain.quiz.dto.QuizResponse;
import com.ongodmatchu.domain.quiz.dto.QuizShareResponse;
import com.ongodmatchu.domain.quiz.dto.QuizUpdateRequest;
import com.ongodmatchu.domain.quiz.service.QuizService;
import com.ongodmatchu.domain.quiz.service.QuizShareService;
import com.ongodmatchu.domain.quiz.service.QuizStarService;
import com.ongodmatchu.global.response.ApiResponse;
import com.ongodmatchu.global.web.AnonIdCookieFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
@Tag(
    name = "Quiz",
    description =
        "퀴즈 생성/조회/수정/삭제 + 좋아요/공유/플레이 카운터. visibility 정책은 docs/api-development.md#71-visibility-public--private")
public class QuizController {

  private final QuizService quizService;
  private final QuizStarService quizStarService;
  private final QuizShareService quizShareService;

  @Operation(summary = "카테고리 목록", description = "퀴즈 카테고리 9종 (영문 키 + 한국어 라벨). 화이트리스트")
  @GetMapping("/categories")
  public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
    return ResponseEntity.ok(ApiResponse.ok(quizService.getCategories()));
  }

  @Operation(
      summary = "공개 퀴즈 목록",
      description =
          "PUBLIC 퀴즈만. category 파라미터로 필터. 비로그인 허용. 기본 정렬 = playCount DESC, createdAt DESC (tiebreaker). 인증 시 isStarred 채움, 비로그인은 null")
  @GetMapping
  public ResponseEntity<ApiResponse<Page<QuizResponse>>> getQuizList(
      @RequestParam(required = false) String category,
      @PageableDefault(
              size = 12,
              sort = {"playCount", "createdAt"},
              direction = Sort.Direction.DESC)
          Pageable pageable,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    Long viewerId = userDetails != null ? userDetails.getUser().getId() : null;
    return ResponseEntity.ok(ApiResponse.ok(quizService.getQuizList(category, viewerId, pageable)));
  }

  @Operation(
      summary = "퀴즈 단건 조회",
      description = "PRIVATE 퀴즈는 본인만 조회 가능. 외부 뷰어가 PRIVATE 접근 시 QUIZ_NOT_FOUND(404) 로 정보 노출 최소화")
  @GetMapping("/{quizId}")
  public ResponseEntity<ApiResponse<QuizDetailResponse>> getQuizDetail(
      @PathVariable Long quizId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    Long viewerId = userDetails != null ? userDetails.getUser().getId() : null;
    return ResponseEntity.ok(ApiResponse.ok(quizService.getQuizDetail(quizId, viewerId)));
  }

  @Operation(
      summary = "퀴즈 생성",
      description =
          "visibility 미지정 시 기본 PRIVATE (임시저장 효과). 가능 에러: INVALID_CATEGORY(400), INVALID_UPLOAD_KEY/UPLOAD_VERIFICATION_FAILED(400/422)")
  @PostMapping
  public ResponseEntity<ApiResponse<QuizResponse>> createQuiz(
      @Valid @RequestBody QuizCreateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    QuizResponse response = quizService.createQuiz(userDetails.getUser().getId(), request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(
      summary = "플레이 카운터 증가",
      description = "비로그인 허용. PRIVATE 퀴즈는 본인만 호출 가능 (외부는 QUIZ_NOT_FOUND)")
  @PostMapping("/{quizId}/play")
  public ResponseEntity<ApiResponse<Void>> incrementPlayCount(
      @PathVariable Long quizId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    Long viewerId = userDetails != null ? userDetails.getUser().getId() : null;
    quizService.incrementPlayCount(quizId, viewerId);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  @Operation(
      summary = "공유 카운터 증가 (사용자/익명 단위 중복 방지)",
      description =
          "로그인 사용자는 user_id, 비로그인은 anon_id 쿠키로 식별. 같은 식별자는 같은 퀴즈에 대해 1회만 카운트 증가. "
              + "이미 공유한 식별자면 alreadyShared=true 로 응답 (카운트는 그대로). "
              + "PRIVATE 퀴즈는 본인만 호출 가능 (외부는 QUIZ_NOT_FOUND).")
  @PostMapping("/{quizId}/share")
  public ResponseEntity<ApiResponse<QuizShareResponse>> share(
      @PathVariable Long quizId,
      HttpServletRequest httpRequest,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    Long userId = userDetails != null ? userDetails.getUser().getId() : null;
    String anonId = (String) httpRequest.getAttribute(AnonIdCookieFilter.REQUEST_ATTRIBUTE);
    return ResponseEntity.ok(ApiResponse.ok(quizShareService.recordShare(quizId, userId, anonId)));
  }

  @Operation(
      summary = "퀴즈 메타 + questions 수정",
      description =
          "title/description/category/thumbnailKey/visibility 옵셔널. questions 도 옵셔널 — null 이면 미변경, 배열이면 id 유지 PUT diff (id 있음=기존 갱신, 없음=신규 추가, payload 에서 빠진 기존 id=삭제). orderNum 은 payload 순서로 재할당. plays/stars/comments/shares 카운터는 보존, questions 만 변경돼도 updatedAt 갱신. 가능 에러: QUIZ_NOT_FOUND(404), QUIZ_FORBIDDEN(403), INVALID_CATEGORY(400), QUESTION_NOT_FOUND(404), INVALID_UPLOAD_KEY(400), UPLOAD_VERIFICATION_FAILED(422)")
  @PatchMapping("/{quizId}")
  public ResponseEntity<ApiResponse<QuizResponse>> updateQuiz(
      @PathVariable Long quizId,
      @Valid @RequestBody QuizUpdateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    QuizResponse response = quizService.updateQuiz(userDetails.getUser().getId(), quizId, request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(
      summary = "퀴즈 삭제",
      description =
          "questions + thumbnail/question 이미지 S3 객체까지 best-effort 정리. 가능 에러: QUIZ_NOT_FOUND(404), QUIZ_FORBIDDEN(403)")
  @DeleteMapping("/{quizId}")
  public ResponseEntity<ApiResponse<Void>> deleteQuiz(
      @PathVariable Long quizId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    quizService.deleteQuiz(userDetails.getUser().getId(), quizId);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  @Operation(
      summary = "퀴즈 좋아요 (스타) 누름",
      description = "멱등 토글 ON. 인증 필수. PRIVATE 퀴즈는 본인만 (외부 QUIZ_NOT_FOUND)")
  @PutMapping("/{quizId}/star")
  public ResponseEntity<ApiResponse<Void>> star(
      @PathVariable Long quizId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    quizStarService.star(userDetails.getUser().getId(), quizId);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  @Operation(summary = "퀴즈 좋아요 (스타) 취소", description = "멱등. 누른 적 없어도 200. PRIVATE 퀴즈는 본인만")
  @DeleteMapping("/{quizId}/star")
  public ResponseEntity<ApiResponse<Void>> unstar(
      @PathVariable Long quizId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    quizStarService.unstar(userDetails.getUser().getId(), quizId);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}
