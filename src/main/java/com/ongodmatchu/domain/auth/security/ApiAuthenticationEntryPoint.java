package com.ongodmatchu.domain.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 인증되지 않은 요청에 {@link ApiResponse} 포맷으로 401 을 반환한다. */
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
    response.setStatus(errorCode.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(
        response.getWriter(), ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
  }
}
