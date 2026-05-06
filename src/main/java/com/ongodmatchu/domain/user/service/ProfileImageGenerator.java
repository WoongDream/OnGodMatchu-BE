package com.ongodmatchu.domain.user.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 닉네임 첫 1자 + **랜덤 단색 배경** 의 SVG 이니셜 이미지를 생성한다. 호출마다 색이 달라져 "기본 이미지" 버튼을 다시 누르면 새 색이 나오는 UX 를 지원한다.
 * 클라이언트 폰트 렌더링에 위임하므로 컨테이너 폰트 패키지에 의존하지 않는다.
 */
@Component
public class ProfileImageGenerator {

  public static final String CONTENT_TYPE = "image/svg+xml";

  private static final String[] PALETTE = {
    "#F87171", "#FB923C", "#FBBF24", "#34D399", "#22D3EE",
    "#60A5FA", "#818CF8", "#A78BFA", "#F472B6", "#94A3B8"
  };

  private static final int SIZE = 256;

  private final SecureRandom random = new SecureRandom();

  public byte[] generateSvg(String nickname) {
    String letter = firstCharacter(nickname);
    String background = pickRandomColor();
    String svg =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\""
            + SIZE
            + "\" height=\""
            + SIZE
            + "\" viewBox=\"0 0 "
            + SIZE
            + " "
            + SIZE
            + "\">"
            + "<rect width=\"100%\" height=\"100%\" fill=\""
            + background
            + "\"/>"
            + "<text x=\"50%\" y=\"50%\" dy=\".35em\" text-anchor=\"middle\""
            + " font-family=\"-apple-system,BlinkMacSystemFont,'Segoe UI','Apple SD Gothic Neo','Noto Sans KR',sans-serif\""
            + " font-size=\"140\" font-weight=\"600\" fill=\"#FFFFFF\">"
            + escapeXml(letter)
            + "</text>"
            + "</svg>";
    return svg.getBytes(StandardCharsets.UTF_8);
  }

  private String firstCharacter(String nickname) {
    if (nickname == null || nickname.isEmpty()) {
      return "?";
    }
    int codePoint = nickname.codePointAt(0);
    return new String(Character.toChars(codePoint)).toUpperCase();
  }

  private String pickRandomColor() {
    return PALETTE[random.nextInt(PALETTE.length)];
  }

  private String escapeXml(String s) {
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
