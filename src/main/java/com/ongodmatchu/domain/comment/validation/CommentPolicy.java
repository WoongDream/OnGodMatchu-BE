package com.ongodmatchu.domain.comment.validation;

import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.text.Normalizer;
import org.springframework.stereotype.Component;

@Component
public class CommentPolicy {

  public static final int MAX_LENGTH = 500;
  public static final int MIN_LENGTH = 1;

  /** trim + NFC 정규화. 빈 문자열은 정책 위반. */
  public String normalize(String raw) {
    if (raw == null) {
      throw new BusinessException(ErrorCode.INVALID_COMMENT_FORMAT, "댓글 내용을 입력해주세요.");
    }
    return Normalizer.normalize(raw.trim(), Normalizer.Form.NFC);
  }

  public void enforce(String normalized) {
    int length = normalized.codePointCount(0, normalized.length());
    if (length < MIN_LENGTH) {
      throw new BusinessException(ErrorCode.INVALID_COMMENT_FORMAT, "댓글 내용을 입력해주세요.");
    }
    if (length > MAX_LENGTH) {
      throw new BusinessException(
          ErrorCode.INVALID_COMMENT_FORMAT, "댓글은 " + MAX_LENGTH + "자 이하여야 합니다.");
    }
  }
}
