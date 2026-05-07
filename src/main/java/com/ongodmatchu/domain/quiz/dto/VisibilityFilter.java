package com.ongodmatchu.domain.quiz.dto;

/** 내가 만든 퀴즈 목록 visibility 필터 — `ALL` 은 PUBLIC + PRIVATE 모두 포함. */
public enum VisibilityFilter {
  ALL,
  PUBLIC,
  PRIVATE;

  public static VisibilityFilter fromKey(String key) {
    if (key == null || key.isBlank()) {
      return ALL;
    }
    String upper = key.trim().toUpperCase();
    for (VisibilityFilter f : values()) {
      if (f.name().equals(upper)) {
        return f;
      }
    }
    return ALL;
  }
}
