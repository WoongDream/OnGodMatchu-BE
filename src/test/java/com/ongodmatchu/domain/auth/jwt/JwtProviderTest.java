package com.ongodmatchu.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JwtProvider 테스트")
class JwtProviderTest {

  private JwtProvider jwtProvider;
  private String secret;
  private long accessTokenExpiry;
  private long refreshTokenExpiry;

  @BeforeEach
  void setUp() {
    secret =
        Base64.getEncoder()
            .encodeToString(
                "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm".getBytes());
    accessTokenExpiry = 3600000; // 1시간
    refreshTokenExpiry = 86400000; // 24시간
    jwtProvider = new JwtProvider(secret, accessTokenExpiry, refreshTokenExpiry);
  }

  @Test
  @DisplayName("정상: 유효한 userId로 access 토큰 생성 성공")
  void generateAccessToken_withValidUserId_returnsValidToken() {
    // given
    Long userId = 1L;

    // when
    String token = jwtProvider.generateAccessToken(userId);

    // then
    assertThat(token).isNotNull();
    assertThat(token).isNotEmpty();
    assertThat(token.split("\\.")).hasSize(3); // JWT는 3개 부분으로 나뉨
  }

  @Test
  @DisplayName("정상: 생성된 access 토큰의 subject가 userId를 문자열로 변환한 값과 동일")
  void generateAccessToken_tokenSubjectEqualsUserId() {
    // given
    Long userId = 42L;

    // when
    String token = jwtProvider.generateAccessToken(userId);

    // then
    Long extractedUserId = jwtProvider.getUserId(token);
    assertThat(extractedUserId).isEqualTo(userId);
  }

  @Test
  @DisplayName("정상: 유효한 userId로 refresh 토큰 생성 성공")
  void generateRefreshToken_withValidUserId_returnsValidToken() {
    // given
    Long userId = 1L;

    // when
    String token = jwtProvider.generateRefreshToken(userId);

    // then
    assertThat(token).isNotNull();
    assertThat(token).isNotEmpty();
    assertThat(token.split("\\.")).hasSize(3);
  }

  @Test
  @DisplayName("정상: refresh 토큰에서 userId 추출 성공")
  void generateRefreshToken_tokenSubjectEqualsUserId() {
    // given
    Long userId = 99L;

    // when
    String token = jwtProvider.generateRefreshToken(userId);

    // then
    Long extractedUserId = jwtProvider.getUserId(token);
    assertThat(extractedUserId).isEqualTo(userId);
  }

  @Test
  @DisplayName("정상: access 토큰과 refresh 토큰의 expiry 비교 (refresh가 더 길어야 함)")
  void tokenExpiry_refreshTokenShorterThanAccessToken() {
    // given
    Long userId = 1L;

    // when
    String accessToken = jwtProvider.generateAccessToken(userId);
    String refreshToken = jwtProvider.generateRefreshToken(userId);

    // then - refresh token이 access token보다 later expiration을 가져야 함
    SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    Claims accessClaims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(accessToken).getPayload();
    Claims refreshClaims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(refreshToken).getPayload();

    Date accessExpiration = accessClaims.getExpiration();
    Date refreshExpiration = refreshClaims.getExpiration();

    assertThat(refreshExpiration.getTime()).isGreaterThan(accessExpiration.getTime());
  }

  @Test
  @DisplayName("정상: 유효한 토큰에서 userId 추출 성공")
  void getUserId_withValidToken_returnsUserId() {
    // given
    Long expectedUserId = 123L;
    String token = jwtProvider.generateAccessToken(expectedUserId);

    // when
    Long extractedUserId = jwtProvider.getUserId(token);

    // then
    assertThat(extractedUserId).isEqualTo(expectedUserId);
  }

  @Test
  @DisplayName("에러: 유효하지 않은 토큰에서 userId 추출 시 INVALID_TOKEN 예외 발생")
  void getUserId_withInvalidToken_throwsBusinessException() {
    // given
    String invalidToken = "invalid.token.here";

    // when & then
    assertThatThrownBy(() -> jwtProvider.getUserId(invalidToken))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("에러: 빈 문자열 토큰에서 userId 추출 시 INVALID_TOKEN 예외 발생")
  void getUserId_withEmptyToken_throwsBusinessException() {
    // given
    String emptyToken = "";

    // when & then
    assertThatThrownBy(() -> jwtProvider.getUserId(emptyToken))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("에러: null 토큰에서 userId 추출 시 예외 발생")
  void getUserId_withNullToken_throwsException() {
    // given
    String nullToken = null;

    // when & then
    assertThatThrownBy(() -> jwtProvider.getUserId(nullToken)).isInstanceOf(Exception.class);
  }

  @Test
  @DisplayName("정상: 유효한 토큰 검증 성공 (예외 없음)")
  void validate_withValidToken_succeeds() {
    // given
    Long userId = 1L;
    String token = jwtProvider.generateAccessToken(userId);

    // when & then - 예외가 발생하지 않아야 함
    jwtProvider.validate(token); // 예외가 발생하지 않으면 성공
  }

  @Test
  @DisplayName("에러: 유효하지 않은 토큰 검증 시 INVALID_TOKEN 예외 발생")
  void validate_withInvalidToken_throwsBusinessException() {
    // given
    String invalidToken = "invalid.token.structure";

    // when & then
    assertThatThrownBy(() -> jwtProvider.validate(invalidToken))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("에러: 빈 문자열 토큰 검증 시 INVALID_TOKEN 예외 발생")
  void validate_withEmptyToken_throwsBusinessException() {
    // given
    String emptyToken = "";

    // when & then
    assertThatThrownBy(() -> jwtProvider.validate(emptyToken))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("경계값: userId = 1로 access 토큰 생성")
  void generateAccessToken_withUserIdOne_returnsValidToken() {
    // given
    Long userId = 1L;

    // when
    String token = jwtProvider.generateAccessToken(userId);
    Long extractedUserId = jwtProvider.getUserId(token);

    // then
    assertThat(extractedUserId).isEqualTo(1L);
  }

  @Test
  @DisplayName("경계값: userId = Long.MAX_VALUE로 access 토큰 생성")
  void generateAccessToken_withMaxLongValue_returnsValidToken() {
    // given
    Long userId = Long.MAX_VALUE;

    // when
    String token = jwtProvider.generateAccessToken(userId);
    Long extractedUserId = jwtProvider.getUserId(token);

    // then
    assertThat(extractedUserId).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  @DisplayName("경계값: userId = 1로 refresh 토큰 생성")
  void generateRefreshToken_withUserIdOne_returnsValidToken() {
    // given
    Long userId = 1L;

    // when
    String token = jwtProvider.generateRefreshToken(userId);
    Long extractedUserId = jwtProvider.getUserId(token);

    // then
    assertThat(extractedUserId).isEqualTo(1L);
  }

  @Test
  @DisplayName("경계값: userId = Long.MAX_VALUE로 refresh 토큰 생성")
  void generateRefreshToken_withMaxLongValue_returnsValidToken() {
    // given
    Long userId = Long.MAX_VALUE;

    // when
    String token = jwtProvider.generateRefreshToken(userId);
    Long extractedUserId = jwtProvider.getUserId(token);

    // then
    assertThat(extractedUserId).isEqualTo(Long.MAX_VALUE);
  }
}
