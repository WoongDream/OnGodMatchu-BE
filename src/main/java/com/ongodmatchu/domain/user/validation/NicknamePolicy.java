package com.ongodmatchu.domain.user.validation;

import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 닉네임 형식 정책(길이·허용 문자)만 담당. 예약어/금지어 차단은 DB 기반 {@code ForbiddenNicknameService} 로 이관(가입·변경 경로에서 형식 검증
 * 직후 별도 호출).
 */
@Component
public class NicknamePolicy {

  private static final int MIN_LENGTH = 2;
  private static final int MAX_LENGTH = 10;
  private static final Pattern ALLOWED = Pattern.compile("^[가-힣A-Za-z0-9_]+$");

  public void enforce(String normalized) {
    if (normalized == null || normalized.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_NICKNAME_FORMAT, "닉네임을 입력해주세요.");
    }
    int length = normalized.codePointCount(0, normalized.length());
    if (length < MIN_LENGTH || length > MAX_LENGTH) {
      throw new BusinessException(
          ErrorCode.INVALID_NICKNAME_FORMAT, "닉네임은 " + MIN_LENGTH + "~" + MAX_LENGTH + "자여야 합니다.");
    }
    if (!ALLOWED.matcher(normalized).matches()) {
      throw new BusinessException(
          ErrorCode.INVALID_NICKNAME_FORMAT, "닉네임은 한글·영문·숫자·언더스코어(_)만 사용할 수 있습니다.");
    }
  }

  public boolean isValid(String normalized) {
    try {
      enforce(normalized);
      return true;
    } catch (BusinessException e) {
      return false;
    }
  }
}
