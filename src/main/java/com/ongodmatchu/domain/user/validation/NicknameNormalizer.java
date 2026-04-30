package com.ongodmatchu.domain.user.validation;

import java.text.Normalizer;
import org.springframework.stereotype.Component;

@Component
public class NicknameNormalizer {

  public String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    return Normalizer.normalize(raw.trim(), Normalizer.Form.NFC);
  }
}
