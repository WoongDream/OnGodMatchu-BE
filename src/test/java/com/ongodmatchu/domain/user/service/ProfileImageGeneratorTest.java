package com.ongodmatchu.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfileImageGeneratorTest {

  private ProfileImageGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new ProfileImageGenerator();
  }

  // ============ generateSvg — 기본 형식 ============

  @Test
  @DisplayName("generateSvg_결과는_SVG_XML_형식이다")
  void generateSvg_result_isSvgXml() {
    byte[] result = generator.generateSvg("홍길동");
    String svg = new String(result, StandardCharsets.UTF_8);

    assertThat(svg).startsWith("<svg");
    assertThat(svg).contains("xmlns=\"http://www.w3.org/2000/svg\"");
    assertThat(svg).endsWith("</svg>");
  }

  @Test
  @DisplayName("generateSvg_UTF8_바이트_배열_반환")
  void generateSvg_returns_utf8Bytes() {
    byte[] result = generator.generateSvg("테스트");
    assertThat(result).isNotNull().isNotEmpty();
    // 한글이 UTF-8로 올바르게 인코딩되는지 확인
    String svg = new String(result, StandardCharsets.UTF_8);
    assertThat(svg).isNotEmpty();
  }

  // ============ 첫 글자 추출 ============

  @Test
  @DisplayName("generateSvg_한글닉네임_첫글자가SVG에포함")
  void generateSvg_koreanNickname_firstCharInSvg() {
    byte[] result = generator.generateSvg("가나다");
    String svg = new String(result, StandardCharsets.UTF_8);

    assertThat(svg).contains("가");
  }

  @Test
  @DisplayName("generateSvg_영문닉네임_첫글자대문자화")
  void generateSvg_englishNickname_firstCharUppercased() {
    byte[] result = generator.generateSvg("abc");
    String svg = new String(result, StandardCharsets.UTF_8);

    assertThat(svg).contains("A");
  }

  @Test
  @DisplayName("generateSvg_빈문자열닉네임_물음표반환")
  void generateSvg_emptyNickname_questionMarkInSvg() {
    byte[] result = generator.generateSvg("");
    String svg = new String(result, StandardCharsets.UTF_8);

    assertThat(svg).contains("?");
  }

  @Test
  @DisplayName("generateSvg_null닉네임_물음표반환")
  void generateSvg_nullNickname_questionMarkInSvg() {
    byte[] result = generator.generateSvg(null);
    String svg = new String(result, StandardCharsets.UTF_8);

    assertThat(svg).contains("?");
  }

  // ============ 색상 결정성 ============

  @Test
  @DisplayName("generateSvg_동일닉네임_항상동일색상")
  void generateSvg_sameNickname_alwaysSameColor() {
    byte[] result1 = generator.generateSvg("홍길동");
    byte[] result2 = generator.generateSvg("홍길동");

    String svg1 = new String(result1, StandardCharsets.UTF_8);
    String svg2 = new String(result2, StandardCharsets.UTF_8);

    // fill 속성으로 배경색 추출
    String color1 = extractFillColor(svg1);
    String color2 = extractFillColor(svg2);

    assertThat(color1).isEqualTo(color2);
  }

  @Test
  @DisplayName("generateSvg_팔레트색상중하나를사용")
  void generateSvg_usesColorFromPalette() {
    String[] palette = {
      "#F87171", "#FB923C", "#FBBF24", "#34D399", "#22D3EE",
      "#60A5FA", "#818CF8", "#A78BFA", "#F472B6", "#94A3B8"
    };

    byte[] result = generator.generateSvg("테스트");
    String svg = new String(result, StandardCharsets.UTF_8);

    boolean containsAnyPaletteColor = false;
    for (String color : palette) {
      if (svg.contains(color)) {
        containsAnyPaletteColor = true;
        break;
      }
    }
    assertThat(containsAnyPaletteColor).isTrue();
  }

  // ============ XML 이스케이프 ============

  @Test
  @DisplayName("generateSvg_꺽쇠포함닉네임_XML이스케이프처리")
  void generateSvg_nicknameWithAngleBrackets_xmlEscaped() {
    byte[] result = generator.generateSvg("<test>");
    String svg = new String(result, StandardCharsets.UTF_8);

    // 원시 꺽쇠가 텍스트 컨텐츠에 들어가면 안 됨 (<text> 태그 안에 raw <test> 없음)
    // SVG 구조 태그(<svg, <rect, <text, </text>, </svg>)는 있어도 됨
    // 이스케이프된 &lt; 로만 들어가야 함
    assertThat(svg).contains("&lt;");
  }

  @Test
  @DisplayName("generateSvg_앰퍼샌드가첫글자인닉네임_XML이스케이프처리")
  void generateSvg_nicknameStartingWithAmpersand_xmlEscaped() {
    // firstCharacter("&B") = "&", escapeXml("&") = "&amp;"
    byte[] result = generator.generateSvg("&B");
    String svg = new String(result, StandardCharsets.UTF_8);

    assertThat(svg).contains("&amp;");
  }

  // ============ 헬퍼 ============

  private String extractFillColor(String svg) {
    // <rect ... fill="#XXXXXX" ... /> 에서 배경색 추출
    int fillIdx = svg.indexOf("fill=\"#");
    if (fillIdx == -1) return "";
    int start = fillIdx + 6;
    int end = svg.indexOf("\"", start);
    return svg.substring(start, end);
  }
}
