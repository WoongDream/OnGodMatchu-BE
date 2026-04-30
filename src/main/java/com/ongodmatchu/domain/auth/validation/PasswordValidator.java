package com.ongodmatchu.domain.auth.validation;

import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordValidator {

  private static final int MIN_LENGTH = 10;
  private static final int MAX_LENGTH = 64;

  private final HibpClient hibpClient;

  public void validate(String rawPassword, String email, String nickname) {
    if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
      throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "비밀번호는 10자 이상이어야 합니다.");
    }
    if (rawPassword.length() > MAX_LENGTH) {
      throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "비밀번호는 64자 이하여야 합니다.");
    }
    if (rawPassword.isBlank()) {
      throw new BusinessException(
          ErrorCode.PASSWORD_POLICY_VIOLATION, "비밀번호는 공백 문자만으로 구성될 수 없습니다.");
    }
    if (email != null && rawPassword.equalsIgnoreCase(email)) {
      throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "비밀번호는 이메일과 동일할 수 없습니다.");
    }
    if (nickname != null && rawPassword.equalsIgnoreCase(nickname)) {
      throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "비밀번호는 닉네임과 동일할 수 없습니다.");
    }
    if (hibpClient.isBreached(rawPassword)) {
      throw new BusinessException(ErrorCode.PASSWORD_BREACHED);
    }
  }
}
