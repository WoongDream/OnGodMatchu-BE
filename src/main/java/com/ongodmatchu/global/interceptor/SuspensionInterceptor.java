package com.ongodmatchu.global.interceptor;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 정지(SUSPENDED) 사용자를 비로그인(익명) 사용자 권한 범위로 격하한다. 익명에게 열려 있는 액션(조회·풀이·공유·본인 계정 정리)은 통과시키고, 로그인 사용자 전용
 * 쓰기/생성(퀴즈 CUD·댓글·프로필 변경·스타 등)은 차단한다. 컨트롤러에 분산하지 않고 공통 진입점에서 일괄 검증한다. 정지 만료(suspended_until 경과)는 요청
 * 시점 lazy 판정으로 자동 통과.
 */
@Component
public class SuspensionInterceptor implements HandlerInterceptor {

  private static final AntPathMatcher MATCHER = new AntPathMatcher();

  /** 정지 중에도 허용 — 익명 사용자에게 열려 있는 액션 + 세션 종료/본인 탈퇴. (GET 은 별도로 전부 허용) */
  private static final List<String> ALLOWED_PATTERNS =
      List.of(
          "POST /api/quizzes/*/attempts",
          "POST /api/quizzes/*/play",
          "POST /api/quizzes/*/share",
          "POST /api/quizzes/grade",
          "POST /api/visits",
          "POST /api/auth/logout",
          "POST /api/auth/refresh",
          "DELETE /api/users/me",
          "POST /api/users/me/withdrawal-code",
          "POST /api/users/me/withdrawal-code/verify",
          "POST /api/users/me/notifications/*/read");

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (HttpMethod.GET.matches(request.getMethod())
        || HttpMethod.OPTIONS.matches(request.getMethod())) {
      return true;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
      return true;
    }
    if (!details.getUser().isSuspended()) {
      return true;
    }

    String key = request.getMethod() + " " + request.getRequestURI();
    for (String pattern : ALLOWED_PATTERNS) {
      if (MATCHER.match(pattern, key)) {
        return true;
      }
    }
    throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
  }
}
