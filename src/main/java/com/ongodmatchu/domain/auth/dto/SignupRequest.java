package com.ongodmatchu.domain.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @Email(message = "이메일 형식이 올바르지 않습니다.") @NotBlank(message = "이메일을 입력해주세요.") String email,
    @NotBlank(message = "닉네임을 입력해주세요.") @Size(min = 2, max = 10, message = "닉네임은 2~10자 사이여야 합니다.")
        String nickname,
    @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(min = 10, max = 64, message = "비밀번호는 10자 이상 64자 이하여야 합니다.")
        String password,
    @NotBlank(message = "인증 코드를 입력해주세요.") @Pattern(regexp = "\\d{6}", message = "인증 코드는 6자리 숫자입니다.")
        String code,
    @AssertTrue(message = "이용약관에 동의해야 합니다.") @NotNull Boolean agreedToTerms,
    @AssertTrue(message = "개인정보처리방침에 동의해야 합니다.") @NotNull Boolean agreedToPrivacy,
    Boolean agreedToMarketing) {

  public boolean marketingOptIn() {
    return agreedToMarketing != null && agreedToMarketing;
  }
}
