package com.ongodmatchu.global.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.auth.validation.TermsPolicy;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class TermsAgreementInterceptorTest {

  private TermsAgreementInterceptor interceptor;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private Object handler;

  @BeforeEach
  void setUp() {
    interceptor = new TermsAgreementInterceptor();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    handler = new Object();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private User buildUserWithTerms(String termsVersion, String privacyVersion) {
    User user =
        User.builder()
            .email("test@example.com")
            .nickname("테스터")
            .password("hashed")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    ReflectionTestUtils.setField(user, "termsVersion", termsVersion);
    ReflectionTestUtils.setField(user, "privacyVersion", privacyVersion);
    return user;
  }

  private void setAuthenticatedUser(User user) {
    CustomUserDetails details = new CustomUserDetails(user);
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  @DisplayName("비인증(Authentication=null) 요청은 통과한다")
  void preHandle_noAuthentication_returnsTrue() throws Exception {
    request.setMethod("GET");
    request.setRequestURI("/api/quiz/1");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("principal이 CustomUserDetails가 아닌 익명 사용자는 통과한다")
  void preHandle_nonCustomUserDetailsPrincipal_returnsTrue() throws Exception {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken("anonymousUser", null);
    SecurityContextHolder.getContext().setAuthentication(auth);
    request.setMethod("GET");
    request.setRequestURI("/api/quiz/1");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("화이트리스트: POST /api/users/me/terms-agreement — 약관 미동의 사용자도 통과한다")
  void preHandle_whitelistTermsAgreement_returnsTrue() throws Exception {
    User user = buildUserWithTerms(null, null);
    setAuthenticatedUser(user);
    request.setMethod("POST");
    request.setRequestURI("/api/users/me/terms-agreement");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("화이트리스트: GET /api/users/me — 약관 미동의 사용자도 통과한다")
  void preHandle_whitelistGetMe_returnsTrue() throws Exception {
    User user = buildUserWithTerms(null, null);
    setAuthenticatedUser(user);
    request.setMethod("GET");
    request.setRequestURI("/api/users/me");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("화이트리스트: POST /api/auth/logout — 약관 미동의 사용자도 통과한다")
  void preHandle_whitelistLogout_returnsTrue() throws Exception {
    User user = buildUserWithTerms(null, null);
    setAuthenticatedUser(user);
    request.setMethod("POST");
    request.setRequestURI("/api/auth/logout");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("화이트리스트: DELETE /api/users/me — 약관 미동의 사용자도 통과한다")
  void preHandle_whitelistDeleteMe_returnsTrue() throws Exception {
    User user = buildUserWithTerms(null, null);
    setAuthenticatedUser(user);
    request.setMethod("DELETE");
    request.setRequestURI("/api/users/me");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("화이트리스트: POST /api/users/me/withdrawal-code — 탈퇴 인증 코드 발송은 약관 미동의여도 통과")
  void preHandle_whitelistWithdrawalCode_returnsTrue() throws Exception {
    User user = buildUserWithTerms(null, null);
    setAuthenticatedUser(user);
    request.setMethod("POST");
    request.setRequestURI("/api/users/me/withdrawal-code");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("화이트리스트: POST /api/auth/refresh — 토큰 갱신은 약관 미동의여도 통과")
  void preHandle_whitelistAuthRefresh_returnsTrue() throws Exception {
    User user = buildUserWithTerms(null, null);
    setAuthenticatedUser(user);
    request.setMethod("POST");
    request.setRequestURI("/api/auth/refresh");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("비화이트리스트 경로 + termsVersion=null 사용자 → TERMS_AGREEMENT_OUTDATED 예외")
  void preHandle_nonWhitelist_nullTermsVersion_throwsException() {
    User user = buildUserWithTerms(null, null);
    setAuthenticatedUser(user);
    request.setMethod("GET");
    request.setRequestURI("/api/quiz/1");

    assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TERMS_AGREEMENT_OUTDATED);
  }

  @Test
  @DisplayName("비화이트리스트 경로 + 구버전(0.9) 약관 동의 사용자 → TERMS_AGREEMENT_OUTDATED 예외")
  void preHandle_nonWhitelist_outdatedTermsVersion_throwsException() {
    User user = buildUserWithTerms("0.9", "0.9");
    setAuthenticatedUser(user);
    request.setMethod("POST");
    request.setRequestURI("/api/quiz");

    assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TERMS_AGREEMENT_OUTDATED);
  }

  @Test
  @DisplayName("비화이트리스트 경로 + 현재 버전 약관 동의 사용자 → 통과한다")
  void preHandle_nonWhitelist_currentTermsVersion_returnsTrue() throws Exception {
    User user =
        buildUserWithTerms(TermsPolicy.CURRENT_TERMS_VERSION, TermsPolicy.CURRENT_PRIVACY_VERSION);
    setAuthenticatedUser(user);
    request.setMethod("GET");
    request.setRequestURI("/api/quiz/1");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }
}
