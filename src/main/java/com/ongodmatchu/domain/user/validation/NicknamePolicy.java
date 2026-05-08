package com.ongodmatchu.domain.user.validation;

import com.ongodmatchu.domain.user.entity.AdminAccount;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class NicknamePolicy {

  private static final int MIN_LENGTH = 2;
  private static final int MAX_LENGTH = 10;
  private static final Pattern ALLOWED = Pattern.compile("^[가-힣A-Za-z0-9_]+$");

  /**
   * 사칭 차단용 reserved 닉네임. 표시용 "탈퇴한 사용자"(공백 포함)는 ALLOWED regex 에서 자체 차단되므로, 공백 제거 변형 "탈퇴한사용자"만 별도 등록.
   */
  private static final Set<String> RESERVED = Set.of(AdminAccount.NICKNAME, "탈퇴한사용자");

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
    if (RESERVED.contains(normalized)) {
      throw new BusinessException(ErrorCode.INVALID_NICKNAME_FORMAT, "사용할 수 없는 닉네임입니다.");
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
