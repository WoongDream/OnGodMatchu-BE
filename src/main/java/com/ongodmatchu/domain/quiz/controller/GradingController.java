package com.ongodmatchu.domain.quiz.controller;

import com.ongodmatchu.domain.quiz.dto.GradeRequest;
import com.ongodmatchu.domain.quiz.dto.GradeResponse;
import com.ongodmatchu.domain.quiz.service.GradingService;
import com.ongodmatchu.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class GradingController {

  private final GradingService gradingService;

  @PostMapping("/grade")
  public ResponseEntity<ApiResponse<GradeResponse>> grade(
      @Valid @RequestBody GradeRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(gradingService.grade(request)));
  }
}
