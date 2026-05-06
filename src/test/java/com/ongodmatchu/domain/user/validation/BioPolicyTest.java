package com.ongodmatchu.domain.user.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BioPolicyTest {

  private BioPolicy bioPolicy;

  @BeforeEach
  void setUp() {
    bioPolicy = new BioPolicy();
  }

  // ============ normalize ============

  @Test
  @DisplayName("normalize_null입력_null반환")
  void normalize_null_returnsNull() {
    assertThat(bioPolicy.normalize(null)).isNull();
  }

  @Test
  @DisplayName("normalize_공백만있는문자열_null반환")
  void normalize_blankString_returnsNull() {
    assertThat(bioPolicy.normalize("  ")).isNull();
  }

  @Test
  @DisplayName("normalize_정상문자열_NFC정규화후반환")
  void normalize_normalString_returnsNormalized() {
    assertThat(bioPolicy.normalize("hello")).isEqualTo("hello");
  }

  @Test
  @DisplayName("normalize_앞뒤공백있는문자열_trim후반환")
  void normalize_stringWithSurroundingSpaces_returnsTrimmed() {
    assertThat(bioPolicy.normalize("  hello  ")).isEqualTo("hello");
  }

  @Test
  @DisplayName("normalize_빈문자열_null반환")
  void normalize_emptyString_returnsNull() {
    assertThat(bioPolicy.normalize("")).isNull();
  }

  // ============ enforce ============

  @Test
  @DisplayName("enforce_null입력_예외없음")
  void enforce_null_doesNotThrow() {
    assertThatNoException().isThrownBy(() -> bioPolicy.enforce(null));
  }

  @Test
  @DisplayName("enforce_40자이하_예외없음")
  void enforce_withinLimit_doesNotThrow() {
    assertThatNoException().isThrownBy(() -> bioPolicy.enforce("a".repeat(40)));
  }

  @Test
  @DisplayName("enforce_경계값40자_예외없음")
  void enforce_exactMaxLength_doesNotThrow() {
    String bio = "a".repeat(40);
    assertThatNoException().isThrownBy(() -> bioPolicy.enforce(bio));
  }

  @Test
  @DisplayName("enforce_41자초과_INVALID_BIO_FORMAT예외")
  void enforce_exceedsMaxLength_throwsInvalidBioFormat() {
    String bio = "a".repeat(41);

    assertThatThrownBy(() -> bioPolicy.enforce(bio))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_BIO_FORMAT);
  }

  @Test
  @DisplayName("enforce_짧은문자열_예외없음")
  void enforce_shortString_doesNotThrow() {
    assertThatNoException().isThrownBy(() -> bioPolicy.enforce("안녕"));
  }
}
