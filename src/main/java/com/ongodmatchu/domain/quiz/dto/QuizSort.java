package com.ongodmatchu.domain.quiz.dto;

import org.springframework.data.domain.Sort;

/** 내가 만든 퀴즈 / 외부 뷰어 목록 정렬 옵션. tiebreaker 는 createdAt DESC. */
public enum QuizSort {
  LATEST("updatedAt"),
  PLAYS("playCount"),
  SHARES("shareCount"),
  STARS("starCount"),
  COMMENTS("commentCount");

  private final String property;

  QuizSort(String property) {
    this.property = property;
  }

  public Sort toSort() {
    return Sort.by(Sort.Order.desc(property), Sort.Order.desc("createdAt"));
  }

  public static QuizSort fromKey(String key) {
    if (key == null || key.isBlank()) {
      return LATEST;
    }
    String upper = key.trim().toUpperCase();
    for (QuizSort s : values()) {
      if (s.name().equals(upper)) {
        return s;
      }
    }
    return LATEST;
  }
}
