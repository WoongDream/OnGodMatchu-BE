package com.ongodmatchu.domain.user.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NicknameNormalizerTest {

  private NicknameNormalizer nicknameNormalizer;

  @BeforeEach
  void setUp() {
    nicknameNormalizer = new NicknameNormalizer();
  }

  @Test
  @DisplayName("normalize_null입력_빈문자열반환")
  void normalize_null_returnsEmptyString() {
    assertThat(nicknameNormalizer.normalize(null)).isEqualTo("");
  }

  @Test
  @DisplayName("normalize_앞뒤공백제거")
  void normalize_trimLeadingAndTrailingSpaces() {
    assertThat(nicknameNormalizer.normalize("  hello  ")).isEqualTo("hello");
  }

  @Test
  @DisplayName("normalize_정상입력_그대로반환")
  void normalize_normalInput_returnsUnchanged() {
    assertThat(nicknameNormalizer.normalize("판다1234")).isEqualTo("판다1234");
  }

  @Test
  @DisplayName("normalize_NFD분리한글_NFC결합형으로정규화")
  void normalize_nfdDecomposedKorean_normalizesToNfc() {
    // "가" 를 NFD로 분해하면 ㄱ(U+1100) + ㅏ(U+1161) 두 코드포인트가 된다
    String nfd = Normalizer.normalize("가나다", Normalizer.Form.NFD);
    // NFD 분해 후에는 NFC "가나다"와 동일하지 않다
    assertThat(nfd).isNotEqualTo("가나다");

    String result = nicknameNormalizer.normalize(nfd);

    // normalize 후에는 NFC "가나다"와 일치해야 한다
    assertThat(result).isEqualTo("가나다");
    assertThat(Normalizer.isNormalized(result, Normalizer.Form.NFC)).isTrue();
  }

  @Test
  @DisplayName("normalize_내부공백유지_정책이차단할책임")
  void normalize_internalSpacePreserved() {
    // 내부 공백 제거는 NicknameNormalizer 의 책임이 아니다
    assertThat(nicknameNormalizer.normalize("hello world")).isEqualTo("hello world");
  }
}
