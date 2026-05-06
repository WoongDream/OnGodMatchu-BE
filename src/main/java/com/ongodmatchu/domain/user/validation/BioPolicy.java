package com.ongodmatchu.domain.user.validation;

import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.text.Normalizer;
import org.springframework.stereotype.Component;

@Component
public class BioPolicy {

  public static final int MAX_LENGTH = 40;

  /** null 허용. trim + NFC 정규화 후 빈 문자열은 null 로 취급해 컬럼을 비운다. */
  public String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    String normalized = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC);
    return normalized.isEmpty() ? null : normalized;
  }

  public void enforce(String normalized) {
    if (normalized == null) {
      return;
    }
    if (normalized.codePointCount(0, normalized.length()) > MAX_LENGTH) {
      throw new BusinessException(
          ErrorCode.INVALID_BIO_FORMAT, "한 줄 소개는 " + MAX_LENGTH + "자 이하여야 합니다.");
    }
  }
}
