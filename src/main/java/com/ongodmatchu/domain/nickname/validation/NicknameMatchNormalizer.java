package com.ongodmatchu.domain.nickname.validation;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 차단 닉네임 매칭 전용 정규화 (Tier 0·1). 저장용 {@code NicknameNormalizer}(NFC+trim)와 분리 — 실제 닉네임 저장값을 바꾸지 않고 매칭
 * 비교 키만 만든다.
 *
 * <ul>
 *   <li>Tier 0 — trim + 내부 공백 제거 + 유니코드 정규화 + 대소문자 통일 + 전각→반각(NFKC)
 *   <li>Tier 1 — zero-width·제어문자 제거, 구두점·언더스코어·특수문자 제거(문자·숫자만 남김)
 * </ul>
 *
 * 결과적으로 '관리 자', ' 관리자', 'ＡＤＭＩＮ', 'Admin' → 'admin'/'관리자', '병.신'·'병_신'·zero-width 삽입 → '병신' 으로 수렴.
 */
@Component
public class NicknameMatchNormalizer {

  public String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    // Tier 0: NFKC 가 전각→반각 + 호환 분해 + 트림 대상 정규화, 이어서 소문자 통일
    String tier0 = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    // Tier 1: 문자(letter)·숫자(number) 외 전부 제거 → 공백·구두점·언더스코어·zero-width·제어문자 일괄 차단
    return tier0.replaceAll("[^\\p{L}\\p{N}]", "");
  }
}
