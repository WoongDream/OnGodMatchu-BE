package com.ongodmatchu.domain.user.service;

import com.ongodmatchu.domain.user.repository.UserRepository;
import java.security.SecureRandom;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RandomNicknameGenerator {

  private static final List<String> ADJECTIVES =
      List.of(
          "용감한", "재미있는", "귀여운", "멋진", "신비한", "빛나는", "활발한", "똑똑한", "행복한", "자유로운", "달콤한", "따뜻한",
          "엉뚱한", "느긋한", "씩씩한", "사랑스런", "장난꾸러기", "도도한", "수줍은", "호기심많은", "졸린", "배고픈", "신난", "게으른",
          "부지런한", "우아한", "강력한", "평화로운", "명랑한", "차분한");

  private static final List<String> NOUNS =
      List.of(
          "판다", "호랑이", "사자", "토끼", "여우", "펭귄", "코알라", "부엉이", "다람쥐", "고양이", "강아지", "햄스터", "수달", "고래",
          "돌고래", "거북이", "두더지", "비버", "라쿤", "치타", "사슴", "곰", "늑대", "고슴도치", "알파카", "카피바라", "미어캣",
          "너구리", "양", "기린");

  private static final int MAX_RETRY = 10;

  private final UserRepository userRepository;
  private final SecureRandom random = new SecureRandom();

  public String generate() {
    for (int i = 0; i < MAX_RETRY; i++) {
      String candidate = pick(ADJECTIVES) + pick(NOUNS) + (1000 + random.nextInt(9000));
      if (!userRepository.existsByNickname(candidate)) {
        return candidate;
      }
    }
    return "유저" + System.currentTimeMillis();
  }

  private String pick(List<String> pool) {
    return pool.get(random.nextInt(pool.size()));
  }
}
