package com.ongodmatchu.global.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class SuspensionInterceptorTest {

  private SuspensionInterceptor interceptor;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private Object handler;

  @BeforeEach
  void setUp() {
    interceptor = new SuspensionInterceptor();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    handler = new Object();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private User buildUser(LocalDateTime suspendedUntil) {
    User user =
        User.builder()
            .email("test@example.com")
            .nickname("테스터")
            .password("hashed")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    ReflectionTestUtils.setField(user, "suspendedUntil", suspendedUntil);
    return user;
  }

  private void setAuthenticatedUser(User user) {
    CustomUserDetails details = new CustomUserDetails(user);
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private void request(String method, String uri) {
    request.setMethod(method);
    request.setRequestURI(uri);
  }

  private LocalDateTime future() {
    return LocalDateTime.now().plusDays(1);
  }

  private LocalDateTime past() {
    return LocalDateTime.now().minusDays(1);
  }

  @Test
  @DisplayName("정지 유저라도 GET 요청은 항상 통과한다")
  void preHandle_suspendedUser_getRequest_returnsTrue() throws Exception {
    setAuthenticatedUser(buildUser(future()));
    request("GET", "/api/quizzes/1");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("비인증(Authentication=null) 요청은 통과한다")
  void preHandle_noAuthentication_returnsTrue() throws Exception {
    request("POST", "/api/quizzes");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("principal이 CustomUserDetails가 아닌 익명 사용자는 통과한다")
  void preHandle_nonCustomUserDetailsPrincipal_returnsTrue() throws Exception {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken("anonymousUser", null);
    SecurityContextHolder.getContext().setAuthentication(auth);
    request("POST", "/api/quizzes");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("정지 아님(suspendedUntil=null) 사용자의 쓰기 요청은 통과한다")
  void preHandle_notSuspended_nullUntil_returnsTrue() throws Exception {
    setAuthenticatedUser(buildUser(null));
    request("POST", "/api/quizzes");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("lazy 만료: suspendedUntil 이 과거면 정지 아님 → POST /api/quizzes 통과")
  void preHandle_expiredSuspension_returnsTrue() throws Exception {
    setAuthenticatedUser(buildUser(past()));
    request("POST", "/api/quizzes");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("정지 유저의 POST /api/quizzes(퀴즈 생성)는 ACCOUNT_SUSPENDED 예외")
  void preHandle_suspended_createQuiz_throwsException() {
    setAuthenticatedUser(buildUser(future()));
    request("POST", "/api/quizzes");

    assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
  }

  @Test
  @DisplayName("정지 유저의 POST /api/comments(댓글 쓰기)는 ACCOUNT_SUSPENDED 예외")
  void preHandle_suspended_createComment_throwsException() {
    setAuthenticatedUser(buildUser(future()));
    request("POST", "/api/comments");

    assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
  }

  @Test
  @DisplayName("정지 유저라도 POST /api/quizzes/{id}/attempts(퀴즈 풀이)는 통과한다")
  void preHandle_suspended_attempts_returnsTrue() throws Exception {
    setAuthenticatedUser(buildUser(future()));
    request("POST", "/api/quizzes/123/attempts");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("정지 유저라도 POST /api/quizzes/{id}/share(공유)는 통과한다")
  void preHandle_suspended_share_returnsTrue() throws Exception {
    setAuthenticatedUser(buildUser(future()));
    request("POST", "/api/quizzes/123/share");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("정지 유저라도 POST /api/visits(방문 기록)는 통과한다")
  void preHandle_suspended_visits_returnsTrue() throws Exception {
    setAuthenticatedUser(buildUser(future()));
    request("POST", "/api/visits");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("정지 유저라도 POST /api/auth/logout(로그아웃)은 통과한다")
  void preHandle_suspended_logout_returnsTrue() throws Exception {
    setAuthenticatedUser(buildUser(future()));
    request("POST", "/api/auth/logout");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("정지 유저라도 DELETE /api/users/me(본인 탈퇴)는 통과한다")
  void preHandle_suspended_deleteMe_returnsTrue() throws Exception {
    setAuthenticatedUser(buildUser(future()));
    request("DELETE", "/api/users/me");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("정지 유저라도 POST /api/users/me/withdrawal-code(탈퇴 코드 발송)는 통과한다")
  void preHandle_suspended_withdrawalCode_returnsTrue() throws Exception {
    setAuthenticatedUser(buildUser(future()));
    request("POST", "/api/users/me/withdrawal-code");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
  }
}
