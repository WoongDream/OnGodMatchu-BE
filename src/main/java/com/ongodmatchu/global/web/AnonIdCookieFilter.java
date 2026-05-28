package com.ongodmatchu.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 모든 요청에 익명 사용자 식별 쿠키 `anon_id` 를 보장. 쿠키가 없으면 UUID v4 를 발급해 응답에 Set-Cookie 헤더를 추가하고,
 * 다운스트림(컨트롤러·서비스)이 즉시 사용할 수 있도록 `request.setAttribute("anonId", ...)` 로 노출.
 */
public class AnonIdCookieFilter extends OncePerRequestFilter {

  public static final String COOKIE_NAME = "anon_id";
  public static final String REQUEST_ATTRIBUTE = "anonId";
  private static final Duration MAX_AGE = Duration.ofDays(365);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String anonId = readCookie(request);
    if (anonId == null || anonId.isBlank()) {
      anonId = UUID.randomUUID().toString();
      response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(anonId).toString());
    }
    request.setAttribute(REQUEST_ATTRIBUTE, anonId);
    filterChain.doFilter(request, response);
  }

  private String readCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private ResponseCookie buildCookie(String value) {
    return ResponseCookie.from(COOKIE_NAME, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(MAX_AGE)
        .build();
  }
}
