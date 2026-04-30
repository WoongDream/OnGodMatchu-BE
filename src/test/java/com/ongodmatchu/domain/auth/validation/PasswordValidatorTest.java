package com.ongodmatchu.domain.auth.validation;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordValidatorTest {

  @Mock private HibpClient hibpClient;

  @InjectMocks private PasswordValidator passwordValidator;

  // ============ 길이 검증 ============

  @Test
  @DisplayName("비밀번호_null_최소길이_예외발생")
  void validate_nullPassword_throwsPolicyViolation() {
    assertThatThrownBy(() -> passwordValidator.validate(null, "user@example.com", "nickname"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);

    then(hibpClient).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("비밀번호_9자_최소길이미달_예외발생")
  void validate_tooShort_throwsPolicyViolation() {
    assertThatThrownBy(() -> passwordValidator.validate("short123", "user@example.com", "nickname"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);

    assertThatThrownBy(() -> passwordValidator.validate("short123", "user@example.com", "nickname"))
        .hasMessageContaining("비밀번호는 10자 이상이어야 합니다.");

    then(hibpClient).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("비밀번호_빈문자열_최소길이미달_예외발생")
  void validate_emptyString_throwsPolicyViolation() {
    assertThatThrownBy(() -> passwordValidator.validate("", "user@example.com", "nickname"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);

    then(hibpClient).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("비밀번호_65자_최대길이초과_예외발생")
  void validate_tooLong_throwsPolicyViolation() {
    String tooLong = "a".repeat(65);

    assertThatThrownBy(() -> passwordValidator.validate(tooLong, "user@example.com", "nickname"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);

    assertThatThrownBy(() -> passwordValidator.validate(tooLong, "user@example.com", "nickname"))
        .hasMessageContaining("비밀번호는 64자 이하여야 합니다.");

    then(hibpClient).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("비밀번호_경계값10자_허용")
  void validate_exactlyMinLength_doesNotThrow() {
    String tenChars = "validPass1";
    given(hibpClient.isBreached(tenChars)).willReturn(false);

    assertThatNoException()
        .isThrownBy(() -> passwordValidator.validate(tenChars, "user@example.com", "nickname"));
  }

  @Test
  @DisplayName("비밀번호_경계값64자_허용")
  void validate_exactlyMaxLength_doesNotThrow() {
    String sixtyFourChars = "a".repeat(64);
    given(hibpClient.isBreached(sixtyFourChars)).willReturn(false);

    assertThatNoException()
        .isThrownBy(
            () -> passwordValidator.validate(sixtyFourChars, "user@example.com", "nickname"));
  }

  // ============ 공백 문자 검증 ============

  @Test
  @DisplayName("비밀번호_공백만으로구성_예외발생")
  void validate_whitespaceOnly_throwsPolicyViolation() {
    // 10개 공백 — 길이 검증은 통과하지만 isBlank() 검증에서 실패해야 한다
    String tenSpaces = "          ";

    assertThatThrownBy(() -> passwordValidator.validate(tenSpaces, "user@example.com", "nickname"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);

    assertThatThrownBy(() -> passwordValidator.validate(tenSpaces, "user@example.com", "nickname"))
        .hasMessageContaining("비밀번호는 공백 문자만으로 구성될 수 없습니다.");

    then(hibpClient).shouldHaveNoInteractions();
  }

  // ============ 이메일과 동일 검증 ============

  @Test
  @DisplayName("비밀번호_이메일과동일_예외발생")
  void validate_sameAsEmail_throwsPolicyViolation() {
    String email = "user@example.com";

    assertThatThrownBy(() -> passwordValidator.validate(email, email, "nickname"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);

    assertThatThrownBy(() -> passwordValidator.validate(email, email, "nickname"))
        .hasMessageContaining("비밀번호는 이메일과 동일할 수 없습니다.");

    then(hibpClient).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("비밀번호_이메일과동일_대소문자무시_예외발생")
  void validate_sameAsEmailCaseInsensitive_throwsPolicyViolation() {
    assertThatThrownBy(
            () -> passwordValidator.validate("USER@EXAMPLE.COM", "user@example.com", "nickname"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);

    then(hibpClient).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("비밀번호_이메일null_이메일검증생략")
  void validate_nullEmail_skipsEmailCheck() {
    given(hibpClient.isBreached("validPass1")).willReturn(false);

    assertThatNoException()
        .isThrownBy(() -> passwordValidator.validate("validPass1", null, "nickname"));
  }

  // ============ 닉네임과 동일 검증 ============

  @Test
  @DisplayName("비밀번호_닉네임과동일_예외발생")
  void validate_sameAsNickname_throwsPolicyViolation() {
    String nickname = "myNickname1";

    assertThatThrownBy(() -> passwordValidator.validate(nickname, "user@example.com", nickname))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);

    assertThatThrownBy(() -> passwordValidator.validate(nickname, "user@example.com", nickname))
        .hasMessageContaining("비밀번호는 닉네임과 동일할 수 없습니다.");

    then(hibpClient).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("비밀번호_닉네임과동일_대소문자무시_예외발생")
  void validate_sameAsNicknameCaseInsensitive_throwsPolicyViolation() {
    assertThatThrownBy(
            () -> passwordValidator.validate("MYNICKNAME1", "user@example.com", "myNickname1"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION);

    then(hibpClient).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("비밀번호_닉네임null_닉네임검증생략")
  void validate_nullNickname_skipsNicknameCheck() {
    given(hibpClient.isBreached("validPass1")).willReturn(false);

    assertThatNoException()
        .isThrownBy(() -> passwordValidator.validate("validPass1", "user@example.com", null));
  }

  // ============ HIBP 유출 검증 ============

  @Test
  @DisplayName("비밀번호_HIBP유출이력있음_예외발생")
  void validate_breachedPassword_throwsPasswordBreached() {
    given(hibpClient.isBreached("breachedPass1")).willReturn(true);

    assertThatThrownBy(
            () -> passwordValidator.validate("breachedPass1", "user@example.com", "nickname"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PASSWORD_BREACHED);
  }

  @Test
  @DisplayName("비밀번호_HIBP유출이력없음_예외없음_hibpClient한번호출")
  void validate_notBreached_noExceptionAndHibpCalledOnce() {
    String rawPassword = "safePassword1";
    given(hibpClient.isBreached(rawPassword)).willReturn(false);

    assertThatNoException()
        .isThrownBy(() -> passwordValidator.validate(rawPassword, "user@example.com", "nickname"));

    then(hibpClient).should().isBreached(rawPassword);
  }

  // ============ 검증 순서 — 앞선 위반이 HIBP 호출을 막는다 ============

  @Test
  @DisplayName("길이위반시_HIBP호출없음")
  void validate_lengthViolation_hibpNeverCalled() {
    assertThatThrownBy(() -> passwordValidator.validate("short", "user@example.com", "nickname"))
        .isInstanceOf(BusinessException.class);

    then(hibpClient).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("이메일동일위반시_HIBP호출없음")
  void validate_emailViolation_hibpNeverCalled() {
    String email = "user@example.com";

    assertThatThrownBy(() -> passwordValidator.validate(email, email, "nickname"))
        .isInstanceOf(BusinessException.class);

    then(hibpClient).shouldHaveNoInteractions();
  }
}
