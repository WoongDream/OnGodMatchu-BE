package com.ongodmatchu.domain.auth.oauth2;

import com.ongodmatchu.domain.auth.entity.RefreshToken;
import com.ongodmatchu.domain.auth.jwt.JwtProvider;
import com.ongodmatchu.domain.auth.repository.RefreshTokenRepository;
import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final JwtProvider jwtProvider;
  private final RefreshTokenRepository refreshTokenRepository;

  @Value("${app.frontend-url}")
  private String frontendUrl;

  @Value("${jwt.refresh-token-expiry}")
  private long refreshTokenExpiry;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
    Long userId = userDetails.getUser().getId();

    String accessToken = jwtProvider.generateAccessToken(userId);
    String refreshTokenValue = jwtProvider.generateRefreshToken(userId);

    refreshTokenRepository.deleteByUserId(userId);
    refreshTokenRepository.save(
        RefreshToken.builder()
            .userId(userId)
            .token(refreshTokenValue)
            .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiry / 1000))
            .build());

    String redirectUrl =
        UriComponentsBuilder.fromUriString(frontendUrl + "/oauth2/callback")
            .queryParam("accessToken", accessToken)
            .queryParam("refreshToken", refreshTokenValue)
            .build()
            .toUriString();

    getRedirectStrategy().sendRedirect(request, response, redirectUrl);
  }
}
