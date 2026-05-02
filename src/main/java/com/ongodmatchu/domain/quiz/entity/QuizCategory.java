package com.ongodmatchu.domain.quiz.entity;

import java.util.Arrays;
import java.util.Optional;

public enum QuizCategory {
  ENTERTAINMENT("entertainment", "연예인"),
  MOVIE("movie", "영화"),
  DRAMA("drama", "드라마"),
  ANIME("anime", "애니메이션"),
  GAME("game", "게임"),
  MUSIC("music", "음악"),
  SPORTS("sports", "스포츠"),
  GENERAL("general", "상식"),
  ETC("etc", "기타");

  private final String key;
  private final String label;

  QuizCategory(String key, String label) {
    this.key = key;
    this.label = label;
  }

  public String getKey() {
    return key;
  }

  public String getLabel() {
    return label;
  }

  public static Optional<QuizCategory> fromKey(String key) {
    if (key == null) {
      return Optional.empty();
    }
    return Arrays.stream(values()).filter(c -> c.key.equals(key)).findFirst();
  }

  public static boolean isValidKey(String key) {
    return fromKey(key).isPresent();
  }
}
