package com.ongodmatchu.global.interceptor;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.auth.validation.TermsPolicy;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 약관 미동의(또는 구버전 동의) 사용자가 비즈니스 API 를 호출하면 차단. FE 가 응답 코드를 보고 약관 페이지로 라우팅한다. 화이트리스트는 약관 동의 처리 자체와 거부
 * 시 로그아웃/탈퇴 경로만 허용.
 */
@Component
public class TermsAgreementInterceptor implements HandlerInterceptor {

  private static final Set<String> WHITELIST =
      Set.of(
          "POST /api/users/me/terms-agreement",
          "GET /api/users/me",
          "POST /api/auth/logout",
          "DELETE /api/users/me");

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return true;
    }
    Object principal = auth.getPrincipal();
    if (!(principal instanceof CustomUserDetails details)) {
      return true;
    }

    String key = request.getMethod() + " " + request.getRequestURI();
    if (WHITELIST.contains(key)) {
      return true;
    }

    User user = details.getUser();
    if (TermsPolicy.needsAgreement(user.getTermsVersion(), user.getPrivacyVersion())) {
      throw new BusinessException(ErrorCode.TERMS_AGREEMENT_OUTDATED);
    }
    return true;
  }
}
