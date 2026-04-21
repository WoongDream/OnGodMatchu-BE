---
name: AuthService 테스트 패턴
description: AuthService 유닛 테스트 구현 시 발견된 패턴 및 베스트 프랙티스
type: reference
---

## AuthService 테스트 구조

**파일 위치**: `src/test/java/com/ongodmatchu/domain/auth/service/AuthServiceTest.java`

### 테스트 범위
- signup() - 회원가입 (3개 테스트)
- sendVerificationCode() - 인증코드 발송 (3개 테스트)
- verifyEmail() - 이메일 인증 (5개 테스트)
- login() - 로그인 (6개 테스트)
- refresh() - 토큰 갱신 (3개 테스트)
- logout() - 로그아웃 (2개 테스트)
- issueTokens() - private 메서드 (간접 테스트)

**총 22개 테스트 메서드**

### 주요 Mock 의존성
```java
@Mock private UserRepository userRepository;
@Mock private EmailVerificationRepository emailVerificationRepository;
@Mock private RefreshTokenRepository refreshTokenRepository;
@Mock private JwtProvider jwtProvider;
@Mock private PasswordEncoder passwordEncoder;
@Mock private MailService mailService;
```

### 테스트 작성 팁

1. **ArgumentCaptor 사용**
   - 저장된 엔티티의 속성을 상세히 검증할 때 사용
   - Mockito는 람다식 커스텀 matcher를 직접 지원하지 않음
   ```java
   ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
   then(userRepository).should().save(userCaptor.capture());
   User savedUser = userCaptor.getValue();
   assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
   ```

2. **ReflectionTestUtils**
   - 엔티티의 id 필드 설정 (생성자로 설정 불가한 경우)
   - @Value 필드 설정 (refreshTokenExpiry 등)
   ```java
   ReflectionTestUtils.setField(user, "id", 1L);
   ReflectionTestUtils.setField(authService, "refreshTokenExpiry", 1209600000L);
   ```

3. **LocalDateTime 검증**
   - 서비스가 `LocalDateTime.now()` 사용 시, Mock 대신 실제 시간 사용
   - 명시적으로 과거/미래 시간 설정: `.plusMinutes(5)`, `.minusMinutes(1)`

4. **BDD Mockito 스타일**
   - given-when-then 패턴
   - `then(mock).should().method()` for verification
   - `then(mock).should(never()).method()` for negative assertions

5. **예외 테스트**
   ```java
   assertThatThrownBy(() -> authService.method(input))
       .isInstanceOf(BusinessException.class)
       .extracting(e -> ((BusinessException) e).getErrorCode())
       .isEqualTo(ErrorCode.EXPECTED_ERROR_CODE);
   ```

### 주의사항
- AuthService는 @Transactional 메서드 많음 - 모킹으로 DB 접근 없음
- sendVerificationCode()는 현재 시간 기반 5분 만료 설정
- generateCode()는 100000~999999 범위의 6자리 숫자 생성
