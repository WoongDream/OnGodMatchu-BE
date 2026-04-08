package com.ongodmatchu.domain.quiz.controller;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.quiz.dto.QuizCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuizDetailResponse;
import com.ongodmatchu.domain.quiz.dto.QuizResponse;
import com.ongodmatchu.domain.quiz.service.QuizService;
import com.ongodmatchu.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

  private final QuizService quizService;

  @GetMapping
  public ResponseEntity<ApiResponse<Page<QuizResponse>>> getQuizList(
      @RequestParam(required = false) String category,
      @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(ApiResponse.ok(quizService.getQuizList(category, pageable)));
  }

  @GetMapping("/{quizId}")
  public ResponseEntity<ApiResponse<QuizDetailResponse>> getQuizDetail(@PathVariable Long quizId) {
    return ResponseEntity.ok(ApiResponse.ok(quizService.getQuizDetail(quizId)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<QuizResponse>> createQuiz(
      @Valid @RequestBody QuizCreateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    QuizResponse response = quizService.createQuiz(userDetails.getUser().getId(), request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @PostMapping("/{quizId}/play")
  public ResponseEntity<ApiResponse<Void>> incrementPlayCount(@PathVariable Long quizId) {
    quizService.incrementPlayCount(quizId);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}
