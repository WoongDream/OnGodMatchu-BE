package com.ongodmatchu.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AnonIdCookieFilterTest {

  private final AnonIdCookieFilter filter = new AnonIdCookieFilter();

  private static String firstSetCookie(MockHttpServletResponse response) {
    List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
    return headers.isEmpty() ? null : headers.get(0);
  }

  private static String extractAnonIdValue(String setCookieHeader) {
    // anon_id=<value>; HttpOnly; Secure; ...
    int eq = setCookieHeader.indexOf('=');
    int sc = setCookieHeader.indexOf(';');
    return setCookieHeader.substring(eq + 1, sc);
  }

  @Test
  @DisplayName("쿠키없음_새UUID발급_SetCookie헤더에속성포함_request속성에anonId세팅")
  void noCookie_issuesNewUuidAndSetsAttributes() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    String setCookie = firstSetCookie(response);
    assertThat(setCookie).isNotNull();
    assertThat(setCookie).startsWith(AnonIdCookieFilter.COOKIE_NAME + "=");
    assertThat(setCookie).contains("HttpOnly");
    assertThat(setCookie).contains("Secure");
    assertThat(setCookie).contains("SameSite=Lax");
    assertThat(setCookie).contains("Path=/");
    assertThat(setCookie).contains("Max-Age=31536000");

    String issued = extractAnonIdValue(setCookie);
    assertThat(request.getAttribute(AnonIdCookieFilter.REQUEST_ATTRIBUTE)).isEqualTo(issued);
    assertThat(chain.getRequest()).isSameAs(request);
    assertThat(chain.getResponse()).isSameAs(response);
  }

  @Test
  @DisplayName("기존쿠키있음_재발급X_request속성에기존값전달")
  void existingCookie_doesNotReissue() throws Exception {
    String existing = UUID.randomUUID().toString();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(AnonIdCookieFilter.COOKIE_NAME, existing));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    assertThat(request.getAttribute(AnonIdCookieFilter.REQUEST_ATTRIBUTE)).isEqualTo(existing);
    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  @DisplayName("빈문자열쿠키_새UUID발급")
  void blankCookie_issuesNewUuid() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(AnonIdCookieFilter.COOKIE_NAME, ""));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    String setCookie = firstSetCookie(response);
    assertThat(setCookie).isNotNull();
    String issued = extractAnonIdValue(setCookie);
    assertThat(issued).isNotBlank();
    assertThat(request.getAttribute(AnonIdCookieFilter.REQUEST_ATTRIBUTE)).isEqualTo(issued);
  }

  @Test
  @DisplayName("발급되는anonId가UUID형식")
  void issuedAnonIdIsValidUuid() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    String setCookie = firstSetCookie(response);
    String issued = extractAnonIdValue(setCookie);
    UUID parsed = UUID.fromString(issued);
    assertThat(parsed.toString()).isEqualTo(issued);
  }

  @Test
  @DisplayName("filterChain은항상한번호출됨_쿠키유무무관")
  void filterChainAlwaysInvokedOnce() throws Exception {
    // 케이스1: 쿠키 없음
    MockHttpServletRequest req1 = new MockHttpServletRequest();
    MockHttpServletResponse res1 = new MockHttpServletResponse();
    CountingFilterChain chain1 = new CountingFilterChain();
    filter.doFilter(req1, res1, chain1);
    assertThat(chain1.count).isEqualTo(1);

    // 케이스2: 쿠키 있음
    MockHttpServletRequest req2 = new MockHttpServletRequest();
    req2.setCookies(new Cookie(AnonIdCookieFilter.COOKIE_NAME, UUID.randomUUID().toString()));
    MockHttpServletResponse res2 = new MockHttpServletResponse();
    CountingFilterChain chain2 = new CountingFilterChain();
    filter.doFilter(req2, res2, chain2);
    assertThat(chain2.count).isEqualTo(1);
  }

  private static class CountingFilterChain extends MockFilterChain {
    int count = 0;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response)
        throws IOException, ServletException {
      count++;
      super.doFilter(request, response);
    }
  }
}
