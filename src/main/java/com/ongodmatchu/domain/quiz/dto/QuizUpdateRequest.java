package com.ongodmatchu.domain.quiz.dto;

import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 퀴즈 메타 + questions 수정. null 인 필드는 변경 없음 (PATCH). questions 는 null 이면 미변경, 빈 배열은 금지, 그 외에는 id 유지
 * PUT 의미론으로 diff 처리: id 있음 → 기존 갱신, id 없음 → 신규, payload 에서 빠진 기존 id → 삭제.
 */
public record QuizUpdateRequest(
    @Size(min = 1, max = 255, message = "제목은 1~255자여야 합니다.") String title,
    String description,
    String category,
    String thumbnailKey,
    QuizVisibility visibility,
    @Valid @Size(min = 1, message = "문제는 1개 이상이어야 합니다.") List<QuestionUpdateRequest> questions) {}
