package com.ongodmatchu.domain.quiz.controller;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.quiz.dto.AttemptCreateRequest;
import com.ongodmatchu.domain.quiz.dto.AttemptResultResponse;
import com.ongodmatchu.domain.quiz.service.QuizAttemptService;
import com.ongodmatchu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes/{quizId}/attempts")
@RequiredArgsConstructor
@Tag(
    name = "QuizAttempt",
    description =
        "퀴즈 풀이 기록 (서버 채점). 비로그인 풀이도 익명(user=null)으로 저장 → 퀴즈 작성자 기준 집계(weeklyPlayCount / correctRate)에 반영")
public class QuizAttemptController {

  private final QuizAttemptService quizAttemptService;

  @Operation(
      summary = "풀이 제출 (A안 서버 채점)",
      description =
          "answers[] 를 서버에서 채점 후 score/total/문항별 결과 반환 + Quiz.playCount 증분 + attempts insert. "
              + "비로그인 시 user=null 로 익명 저장 (응답 attemptId 만 null). 작성자 기준 집계에는 반영, '내 풀이 기록' 에는 미노출. "
              + "PRIVATE 퀴즈 + 외부 뷰어 → QUIZ_NOT_FOUND(404). "
              + "가능 에러: QUIZ_NOT_FOUND(404), QUESTION_NOT_FOUND(404), INVALID_INPUT(400)")
  @PostMapping
  public ResponseEntity<ApiResponse<AttemptResultResponse>> submit(
      @PathVariable Long quizId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody AttemptCreateRequest request) {
    Long viewerId = userDetails != null ? userDetails.getUser().getId() : null;
    AttemptResultResponse response = quizAttemptService.submit(quizId, viewerId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }
}
