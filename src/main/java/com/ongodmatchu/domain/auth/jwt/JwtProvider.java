package com.ongodmatchu.domain.auth.jwt;

import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

  private final SecretKey key;
  private final long accessTokenExpiry;
  private final long refreshTokenExpiry;

  public JwtProvider(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-expiry}") long accessTokenExpiry,
      @Value("${jwt.refresh-token-expiry}") long refreshTokenExpiry) {
    this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    this.accessTokenExpiry = accessTokenExpiry;
    this.refreshTokenExpiry = refreshTokenExpiry;
  }

  public String generateAccessToken(Long userId) {
    return generate(userId, accessTokenExpiry);
  }

  public String generateRefreshToken(Long userId) {
    return generate(userId, refreshTokenExpiry);
  }

  public Long getUserId(String token) {
    return Long.parseLong(parseClaims(token).getSubject());
  }

  public void validate(String token) {
    parseClaims(token);
  }

  private String generate(Long userId, long expiry) {
    Date now = new Date();
    return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(now)
        .expiration(new Date(now.getTime() + expiry))
        .signWith(key)
        .compact();
  }

  private Claims parseClaims(String token) {
    try {
      return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    } catch (ExpiredJwtException e) {
      throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
    } catch (JwtException | IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.INVALID_TOKEN);
    }
  }
}
