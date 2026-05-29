package com.ongodmatchu.domain.quiz.entity;

import java.util.Arrays;
import java.util.Optional;

public enum QuizCategory {
  GAME("game", "게임"),
  MUSIC("music", "음악"),
  CULTURE("culture", "문화"),
  BROADCAST("broadcast", "방송"),
  GENERAL("general", "상식"),
  COMIC("comic", "만화"),
  FOOD("food", "음식"),
  PERSON("person", "인물"),
  SPORTS("sports", "스포츠"),
  MEME("meme", "병맛");

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
