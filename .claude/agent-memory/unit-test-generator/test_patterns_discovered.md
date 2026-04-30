---
name: Test Patterns & Mock Setup Strategies
description: Recurring patterns and mock configurations for OnGodMatchu unit tests
type: reference
---

## Entity ID Setting Pattern (User Test)

Since User entity uses `@NoArgsConstructor` with `AccessLevel.PROTECTED` and id is not included in `@Builder`, use reflection to set id:

```java
private User createUserWithId(Long id, String email, String nickname, AuthProvider provider) {
  User user = User.builder()
      .email(email)
      .nickname(nickname)
      .password("hashedPassword123")
      .provider(provider)
      .emailVerified(true)
      .build();
  try {
    Field idField = User.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(user, id);
  } catch (NoSuchFieldException | IllegalAccessException e) {
    throw new RuntimeException(e);
  }
  return user;
}
```

## JWT Provider Test Setup

For JwtProviderTest, initialize with Base64-encoded secret and concrete expiry times:

```java
@BeforeEach
void setUp() {
  secret = Base64.getEncoder().encodeToString(
      "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm".getBytes());
  accessTokenExpiry = 3600000; // 1시간
  refreshTokenExpiry = 86400000; // 24시간
  jwtProvider = new JwtProvider(secret, accessTokenExpiry, refreshTokenExpiry);
}
```

JwtProvider cannot be mocked easily — test against real JWT generation/parsing to verify token structure and expiry.

## ErrorCode Exception Extraction

Extract ErrorCode from BusinessException:

```java
assertThatThrownBy(() -> service.method())
    .isInstanceOf(BusinessException.class)
    .extracting(e -> ((BusinessException) e).getErrorCode())
    .isEqualTo(ErrorCode.EXPECTED_CODE);
```

## AuthProvider Enum

Located in `com.ongodmatchu.domain.user.entity.AuthProvider` with values: LOCAL, GOOGLE, NAVER, KAKAO.

## UserResponse DTO Pattern

UserResponse is a record. Use `.from(entity)` static method which extracts:
- id, email, nickname, provider.name() (as String)

## Spotless/Format

Run `./gradlew spotlessApply` after test generation to ensure Google Java Format compliance.

## CustomOAuth2UserService Test Strategy

`CustomOAuth2UserService.loadUser()` calls `super.loadUser()` (real HTTP to OAuth provider). Spy-casting to `DefaultOAuth2UserService` stubs the whole method instead of only the super call — the real body never runs.

Correct approach: create an anonymous subclass that overrides `loadUser()`, replicates the business logic body inline (resolveUserInfo → findByEmail → registerUser), and replaces `super.loadUser()` with the pre-built `DefaultOAuth2User` fixture. Pass `null` as request since the override ignores it.

```java
private CustomOAuth2UserService buildServiceWithStubbedSuperLoadUser(OAuth2User stubbedUser) {
  return new CustomOAuth2UserService(userRepository, nicknameGenerator) {
    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
      OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(stubbedUser.getAttributes());
      User user = userRepository.findByEmail(userInfo.getEmail())
          .orElseGet(() -> userRepository.save(User.builder()
              .email(userInfo.getEmail())
              .nickname(nicknameGenerator.generate())
              .provider(AuthProvider.GOOGLE)
              .providerId(userInfo.getId())
              .emailVerified(true)
              .build()));
      return new CustomUserDetails(user);
    }
  };
}
// Call: service.loadUser(null)
```

Verify: `then(nicknameGenerator).should().generate()` for new user; `then(nicknameGenerator).should(never()).generate()` for existing user.

## @RequiredArgsConstructor + @Spy Incompatibility

Classes using `@RequiredArgsConstructor` (no no-arg constructor) cannot be annotated with `@Spy` at the field level — Mockito needs a no-arg constructor to instantiate it.

Fix: instantiate manually in `@BeforeEach`:
```java
private CustomOAuth2UserService service;

@BeforeEach
void setUp() {
  service = Mockito.spy(new CustomOAuth2UserService(userRepository, nicknameGenerator));
}
```
But for `CustomOAuth2UserService` even this spy won't work for super call stubbing — use the anonymous subclass pattern above instead.
