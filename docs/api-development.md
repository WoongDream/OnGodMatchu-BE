# API 개발 가이드

> `conventions.md` 가 코드 스타일이라면, 이 문서는 **API 추가/변경 시 빠뜨리기 쉬운 것** 을 잡기 위한 패턴 카탈로그·체크리스트다.

---

## 1. 새 엔드포인트 추가 체크리스트

```
[ ] DTO 정의 — XxxRequest (검증 어노테이션) / XxxResponse (정적 팩토리 from)
[ ] Service 메서드 — @Transactional(readOnly?) + ErrorCode 사용
[ ] Repository 메서드 — 필요 시 @Query (PG 문법 준수)
[ ] Controller — @AuthenticationPrincipal CustomUserDetails (필수/옵셔널 결정), ApiResponse 래핑
[ ] Swagger — @Operation(summary, description) 1줄 + 가능 에러 코드 (자세한 건 docs ref). 자명하지 않은 응답 필드만 @Schema(description)
[ ] SecurityConfig — 인증/permitAll 매처 (필요 시)
[ ] ErrorCode enum — 신규 케이스 추가
[ ] 마이그레이션 — V{N}__이름.sql (스키마 변경 시)
[ ] 테스트 — 단위 + 슬라이스 (@WebMvcTest 또는 service 단위)
[ ] spotlessApply 통과 + 전체 테스트 그린
```

---

## 2. 인증 / 권한 패턴

### 2.1 @AuthenticationPrincipal — 필수 vs 옵셔널

```java
// 인증 필수 (SecurityConfig 에서 authenticated)
public ResponseEntity<...> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
  Long userId = userDetails.getUser().getId();   // null 검사 불필요
}

// 비로그인 허용 (permitAll 또는 본인/외부 분기)
public ResponseEntity<...> publicEndpoint(
    @AuthenticationPrincipal CustomUserDetails userDetails) {
  Long viewerId = userDetails != null ? userDetails.getUser().getId() : null;
}
```

**자주 하는 실수**: SecurityConfig 가 permitAll 인데 `userDetails` 를 null 체크 안 하고 `.getUser()` 호출 → NPE.

### 2.2 SecurityConfig 매처 추가 순서

`SecurityConfig.filterChain` 에서 위→아래로 첫 매칭. 새 경로 추가 시:

```java
// 1) 명시적 permitAll/authenticated 매처
.requestMatchers(HttpMethod.GET, "/api/users/me", "/api/users/me/quizzes",
    "/api/users/me/profile/stats")
.authenticated()
// 2) catch-all
.anyRequest().authenticated()
```

**자주 하는 실수**: 새 GET 경로를 추가했는데 `/api/users/*` permitAll 이 먼저 매칭해서 인증이 빠지는 경우. 더 구체적 경로를 위에 둘 것.

### 2.3 본인 / 외부 뷰어 분기 (Visibility)

```java
boolean isOwner = viewerUserId != null && viewerUserId.equals(quiz.getUser().getId());
if (quiz.getVisibility() == QuizVisibility.PRIVATE && !isOwner) {
  throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND);  // 403 아닌 404 — 정보 노출 최소화
}
```

- PRIVATE 리소스에 외부 뷰어 접근 → **404 (`QUIZ_NOT_FOUND`)** 로 통일. 403 은 리소스 존재를 노출함.
- 비공개 프로필 + 외부 뷰어 목록 → **빈 페이지** 반환 (예외 던지지 않음).

---

## 3. 응답 포맷 / 에러 처리

### 3.1 ApiResponse 래핑

```java
return ResponseEntity.ok(ApiResponse.ok(response));   // 성공
// 실패는 BusinessException(ErrorCode.X) 던지면 GlobalExceptionHandler 가 변환
```

### 3.2 ErrorCode 일관 사용

신규 에러는 반드시 `ErrorCode` enum 에 추가. 인라인 메시지로 던지지 말 것.

```java
// X
throw new BusinessException("뭔가 잘못됨");
// O
throw new BusinessException(ErrorCode.QUIZ_FORBIDDEN);
```

### 3.3 HTTP 상태 매핑 (이미 GlobalExceptionHandler 처리)

| 상황 | 상태 | 핸들러 |
|---|---|---|
| `BusinessException` | ErrorCode 의 `status` | `handleBusinessException` |
| `@Valid` 본문 검증 실패 | 400 | `handleValidationException` |
| 잘못된 JSON 본문 | 400 | `handleNotReadable` |
| 매핑 없는 경로 | 404 | `handleNoResource` |
| 메서드 미지원 | 405 | `handleMethodNotSupported` |
| `RateLimitException` | 429 + Retry-After | `handleRateLimitException` |
| 그 외 미처리 | 500 + log.error | `handleException` |

---

## 4. DTO / 응답 패턴

### 4.1 정적 팩토리 `from(entity)`

```java
public record QuizResponse(...) {
  public static QuizResponse from(Quiz quiz, String thumbnailUrl) { ... }
}
```

### 4.2 응답 필드 추가/변경 시 — 모든 사용 지점 체크

`QuizResponse` 에 필드 추가했으면:
- `from(...)` 호출처 (Service 의 모든 변환 지점)
- 다른 응답 DTO 가 같은 도메인을 노출 중이면 거기도 (`QuizDetailResponse`, `MyQuizListItemResponse`)
- 테스트의 `new QuizResponse(...)` 직접 생성 부분 (record 라 컴파일 에러 발생 → 빠뜨릴 일 없음)

### 4.3 FE 와 응답 스키마 변경 공유

응답 record 의 **필드 제거/이름 변경/타입 변경** 또는 **에러 코드 추가/변경** 은 BREAKING — FE 동시 배포 필요. PR 본문에 영향 endpoint 와 사유를 명시한다 (형식 자유).

**필드 추가만** 하는 변경은 BREAKING 아니지만 동일하게 PR 본문에 한 줄 남긴다.

### 4.3 비공개 프로필 응답 분기

`getProfile` 은 본인/공개 → `UserResponse`, 비공개+외부 → `PublicUserResponse` (축약). Object 반환 + Jackson 직렬화.

---

## 5. 트랜잭션 / 동시성

### 5.1 readOnly 필수

```java
@Transactional(readOnly = true)
public Page<...> getMyQuizList(...) { ... }
```

### 5.2 카운터 캐시 컬럼 패턴

`Quiz.starCount` / `commentCount` / `shareCount` / `playCount` 모두 캐시 컬럼.

```java
// 엔티티
public void incrementStarCount() { this.starCount++; }
public void decrementStarCount() { if (this.starCount > 0) this.starCount--; }

// 서비스 — @Transactional 안에서 dirty checking 으로 반영
quiz.incrementStarCount();
```

**고동시성 문제**: 단순 `++` 는 lost update 가능. 1차 범위에서는 단일 토글 멱등성 + UNIQUE 제약(`quiz_stars`) 으로 방어. 본격 보강 필요 시 `@Modifying` UPDATE 또는 row lock.

---

## 6. DB / 마이그레이션

### 6.1 파일명

`src/main/resources/db/migration/V{N}__이름.sql`. N 은 순차 (V14 다음 V15). 한 번 머지된 파일은 **수정 금지** (Flyway checksum). 잘못 만들면 새 V 로 복구.

### 6.2 PostgreSQL 문법

| 잘못된 패턴 | 올바른 패턴 |
|---|---|
| `BIGINT NOT NULL AUTO_INCREMENT` | `BIGSERIAL` |
| `TIMESTAMP DEFAULT CURRENT_TIMESTAMP` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `FOREIGN KEY ... REFERENCES users(id)` 따로 선언 | 컬럼 라인에 `BIGINT NOT NULL REFERENCES users(id)` 인라인도 가능 |

### 6.3 신규 컬럼 + 백필

```sql
ALTER TABLE quizzes ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();
UPDATE quizzes SET updated_at = created_at;
```

### 6.4 인덱스 / UNIQUE

```sql
CREATE INDEX idx_quiz_comments_quiz_created ON quiz_comments (quiz_id, created_at DESC);
CONSTRAINT uk_quiz_stars_user_quiz UNIQUE (user_id, quiz_id)
```

---

## 7. 도메인 패턴

### 7.1 Visibility (PUBLIC / PRIVATE)

- 생성 기본값 = PRIVATE (임시저장 효과)
- 공개 목록 / 카테고리 검색 / 외부 뷰어 → PRIVATE 제외
- 단건 조회 / star / comment / share / play → 외부 뷰어가 PRIVATE 접근 시 `QUIZ_NOT_FOUND`

### 7.2 Soft delete

`deleted_at TIMESTAMP NULL` 컬럼 + 엔티티 `softDelete()` / `isDeleted()` 메서드. 목록 조회는 `findByXxxAndDeletedAtIsNull...` 형태.

### 7.3 Policy 패턴

`NicknamePolicy` / `BioPolicy` / `CommentPolicy` 모두 동일 구조:
- `normalize(String raw)` — trim + NFC 정규화
- `enforce(String normalized)` — 길이/형식 검증, 위반 시 `BusinessException` 던짐

서비스에서 `policy.normalize(input)` → `policy.enforce(normalized)` 순으로 호출. DTO `@Valid` 만으로 부족한 도메인 규칙은 Policy 로 분리.

---

## 8. 페이지네이션

```java
@PageableDefault(size = 20) Pageable pageable
```

- size 디폴트 20, **최대 50** (Service 단에서 cap)
- Spring 기본 `?sort=field,desc` 형식은 사용하지 않음. 대신 명시적 `?sort=latest|plays|...` 쿼리 파라미터 + `QuizSort` enum 매핑
- tiebreaker 필요 시 `Sort.by(Order.desc(field), Order.desc("createdAt"))`

---

## 8.5 응답 값 컨벤션

### 시간

응답 DTO 의 시간 필드는 **`OffsetDateTime`** (ISO 8601 + offset). 직렬화 예: `2026-05-07T10:00:00+09:00`.

- Entity / DB 컬럼은 `LocalDateTime` / `TIMESTAMP` 그대로 (서버 타임존 KST 가정)
- 응답 변환은 `global/util/TimeFormat.toResponse(ldt)` 헬퍼 사용 — `+09:00` offset 부착
- 새 응답 DTO 필드 추가 시 직접 `LocalDateTime` 노출 금지. 항상 `OffsetDateTime` + `TimeFormat.toResponse(...)`

이유: `LocalDateTime` 은 타임존 정보 없어 클라이언트 해석이 모호 (특히 Safari iOS 의 `new Date('2024-01-01T00:00:00')` 가 UTC 로 해석되는 케이스 등).

### nullable 의미

수치/통계 필드의 의미를 통일한다.

| 의미 | 표현 |
|---|---|
| 측정값이 0 (예: 좋아요 0개, 시도 1회 + 정답 0개) | **`0`** |
| 데이터 자체가 없음 (예: 시도 0회 → 정답률 측정 불가) | **`null`** |
| 비로그인 사용자에 대한 "현재 사용자가 X 했는지" boolean (예: `isStarred`, 향후 `isOwner`) | **`null`** ("모름") — primitive `boolean` 대신 `Boolean` 사용 |

예시:
- `Quiz.starCount = 0` — 좋아요 한 번도 없음 → `0`
- `MyQuizListItemResponse.correctRate = null` — 풀이 시도 0 → 정답률 산출 불가 → `null`
- `MyQuizListItemResponse.correctRate = 0.0` — 풀이 시도 N + 모두 오답 → `0.0`
- `QuizResponse.isStarred = null` — 비로그인 뷰어 (좋아요 여부 모름) / `true` / `false` — 로그인 뷰어 결과

### profileImageUrl non-null 보장

`UserResponse.profileImageUrl` 은 항상 non-null.
- 가입 시점에 `ProfileImageInitializer` 가 SVG 자동 생성 + S3 업로드 (LOCAL / OAuth 둘 다)
- `DELETE /me/profile-image` 후 key 가 null 이어도 서버 측 `app.profile.default-image-url` 폴백 URL 노출
- FE 가 이니셜 fallback UI 따로 둘 필요 없음

### Page<T> 스키마

Spring Data 기본. FE 가 매번 동일 형태로 처리:

```json
{
  "content": [...],
  "totalElements": 42,
  "totalPages": 3,
  "number": 0,           // 현재 페이지 (0-based)
  "size": 20,
  "first": true,
  "last": false,
  "empty": false
}
```

쿼리 파라미터: `?page=0&size=20`. **Spring 기본 `?sort=field,desc` 형식은 사용하지 않음** — 명시 sort enum (예: `?sort=latest`) 만 의미 있음.

---

## 8.6 인증 / 토큰

### 토큰 TTL

| 토큰 | TTL |
|---|---|
| accessToken | **30분** (1,800,000 ms) |
| refreshToken | **14일** (1,209,600,000 ms) |

`application.yml` 의 `jwt.access-token-expiry` / `jwt.refresh-token-expiry` 로 조정.

### 헤더

```
Authorization: Bearer <accessToken>
```

### Refresh 흐름

`POST /api/auth/refresh { refreshToken }` → 성공 시 새 access + refresh 한 쌍 발급. 실패 시:

| 응답 코드 | 의미 | FE 동작 |
|---|---|---|
| `INVALID_TOKEN` (401) | RT 위조/형식 오류 | 재로그인 |
| `TOKEN_EXPIRED` (401) | RT 만료 | 재로그인 |
| `REFRESH_TOKEN_NOT_FOUND` (401) | DB 에 RT 없음 (이미 무효화됨, 비밀번호 변경 등) | 재로그인 |

### 401 응답 포맷 통일

`ApiAuthenticationEntryPoint` 가 401 을 `ApiResponse` 포맷으로 반환:

```json
{ "success": false, "error": { "code": "UNAUTHORIZED", "message": "인증이 필요합니다." } }
```

FE 인터셉터는 401 받으면 RT 갱신 시도 → 401 재발생 시 재로그인. 코드는 `UNAUTHORIZED`/`INVALID_TOKEN`/`TOKEN_EXPIRED`/`REFRESH_TOKEN_NOT_FOUND` 모두 동일하게 처리해도 됨.

---

## 8.7 Rate Limit 적용 범위

| Endpoint | 제한 | 응답 |
|---|---|---|
| `POST /api/auth/send-verification-code` | 이메일별 60s 쿨다운 / 1h 5회, IP별 1h 10회 | 429 + `Retry-After` 헤더 + `error.retryAfter` (초) |

기타 endpoint(로그인/share 카운터 등)는 후속 작업 (`TODO.md` 참조).

---

## 9. S3 / 파일 업로드 흐름

### 9.1 Presigned PUT 3단계

1. `POST /api/upload/presigned` (퀴즈) 또는 `POST /api/users/me/profile-image` (프로필) → `PresignedUrlResponse { uploadUrl, key, expiresIn, requiredHeaders }`
2. FE 가 `uploadUrl` 로 PUT — **`requiredHeaders` 의 모든 헤더 부착 필수** (보통 `Content-Type` + `x-amz-tagging: status=pending`). 누락 시 S3 가 403
3. `PATCH /api/upload/complete` 또는 `PATCH /api/users/me/profile-image { key }` → BE 가 HEAD 검증 + 태그 제거 + DB COMPLETED

### 9.2 Orphan 정리

- PUT 시 `x-amz-tagging: status=pending` 부착 → S3 라이프사이클 룰이 1day 후 expire
- `/complete` 호출 시 DeleteObjectTagging → 태그 제거 → 영구 보존
- 태그 제거 실패 시 DB 도 PENDING 으로 남겨 사용자가 재시도 가능

### 9.3 자주 하는 실수

- FE 가 PUT 헤더에서 `x-amz-tagging` 빠뜨림 → 403
- `Content-Type` 을 발급 시점과 다르게 보냄 → 서명 불일치 403
- key 를 BE 검증 없이 그대로 사용 → `S3Service.verifyKeyOwnedAndCompleted` 누락 시 위변조 가능

### 9.4 이미지 GET URL 정책 (`thumbnailUrl` / `profileImageUrl`)

응답에 노출되는 이미지 URL 은 **presigned GET URL** (영구 public URL 아님).

- TTL: **1시간** (`VIEW_URL_EXPIRY`). `expiresIn` / `expiresAt` 은 `ViewUrlResponse` 에서만 명시. 일반 응답 (`QuizResponse.thumbnailUrl` 등) 에는 URL 만 포함
- **응답마다 새 URL** — 서명 시각이 다르므로 같은 객체 키여도 query string 이 매번 다름
- 한 응답 내 여러 위치에 같은 key 가 등장하면 동일 URL 사용 (`S3Service.batchPresignViewUrls`)
- 페이지 새로고침 = 새 URL → 브라우저 캐시 미스 (의도된 동작 — 권한 만료 후 무한 보기 방지)

FE 캐싱 가이드:
- `<img src=...>` 의 src 가 매번 바뀌어 캐시 미스 발생. 트래픽 민감하면 short-term 메모리 캐시 (key 단위) 또는 React Query 등의 데이터 캐시 활용
- 사용자 액션 (좋아요 토글 등) 이후 해당 화면 재요청 시 URL 만 새로 받는 정도라 실용적 부담은 작음
- 1시간 이상 페이지 열어둔 채 표시 시 URL 만료 → 보통 `<img>` 가 다시 로드 시도하지 않으므로 큰 문제 없음 (한 번 로드된 이미지는 메모리에 남음). 갱신 필요 시 페이지 reload 또는 응답 재요청

### 9.5 만료 후 재발급 패턴

Presigned URL TTL = **10분** (`UPLOAD_URL_EXPIRY`). 사용자가 페이지 열어둔 채 PUT 시도하면 서명 만료로 403.

권장 패턴:
- FE 는 `expiresIn` (초) 또는 자체 타이머로 만료 임박 시점(예: 9분 경과) 자동 재발급
- 또는 PUT 응답 403 시 1회만 재발급 후 재시도 (무한 루프 방지)

`/complete` 단계는 별도 — 이미 PUT 성공한 후라 만료와 무관.

---

## 10. 테스트 패턴

### 10.1 슬라이스 vs 단위

| 대상 | 도구 | 예시 |
|---|---|---|
| Service 단위 | Mockito + `@ExtendWith(MockitoExtension.class)` | `QuizServiceTest` |
| Controller 슬라이스 | `@WebMvcTest(XxxController.class)` + `@MockitoBean` | `QuizControllerTest` |
| Repository / 통합 | `@DataJpaTest` 또는 `@SpringBootTest` | `QuizRepositoryTest` |

### 10.2 자주 쓰는 트릭

- `ReflectionTestUtils.setField(entity, "id", 1L)` — `@GeneratedValue` 우회해 ID 부여
- `given(repo.method(any())).willAnswer(inv -> inv.getArgument(0))` — save 가 받은 인자 그대로 반환
- `lenient()` — `@BeforeEach` 의 stub 이 모든 테스트에서 안 쓰일 때 `UnnecessaryStubbing` 회피
- `ArgumentCaptor` — 호출된 인자 검증 (`Sort` 객체 / 저장된 엔티티 등)

### 10.3 신규 도메인 추가 시 — `@WebMvcTest` 가 의존하는 빈

`QuizController` 가 `QuizStarService` 추가됐다면 `QuizControllerTest` 에 `@MockitoBean private QuizStarService quizStarService;` 빠뜨리지 말 것. (안 그러면 ApplicationContext 로딩 실패)

---

## 10.4 ErrorCode 카탈로그

전체 에러 코드 표 + 발생 endpoint + FE 처리 패턴은 별도 문서 — `docs/error-codes.md`. 단일 진실 원천은 `ErrorCode.java` (코드 우선).

---

## 11. Swagger / OpenAPI

`/swagger-ui/index.html` (UI), `/v3/api-docs` (JSON 스펙). `OpenApiConfig` 가 Bearer 인증 스킴을 정의해 UI 에서 토큰 입력 후 호출 가능.

### 어노테이션 컨벤션 — **단순하게**

이 문서 (`docs/api-development.md` + `docs/error-codes.md`) 가 단일 진실 원천. Swagger 어노테이션은 endpoint 단위 한 줄 요약 + 가능 에러 코드 목록 정도로만:

- `@Tag(name, description)` — 컨트롤러에 한 번. description 끝에 docs ref 한 줄
- `@Operation(summary, description)` — endpoint 마다. **description 1줄**: 핵심 동작 + 가능 에러 코드 (예: `"PRIVATE 본인만. 가능 에러: QUIZ_NOT_FOUND(404), QUIZ_FORBIDDEN(403)"`)
- `@Schema(description)` — **자명하지 않은 응답 필드만** (`isStarred` nullable 의미, `correctRate` null 의미, `requiredHeaders` 사용법, `OffsetDateTime` KST 등). example 은 보통 생략 (docs 가 더 정확)
- `@ApiResponse(responseCode, description)` — 보통 미사용. `@Operation.description` 한 줄에 다 적는다 (별도 어노테이션 추가는 동기화 부담만 늘어남)

### 상세 정보는 docs 로 ref

긴 설명/예시/매핑 표는 어노테이션이 아니라 `docs/api-development.md` 에 적고, 어노테이션은 그 섹션을 가리키도록. 양쪽 동기화 부담을 한 곳으로 모은다.

---

## 12. 자주 하는 실수 모음 (FAQ)

| 증상 | 원인 / 해결 |
|---|---|
| POST/PATCH 응답 400 + `Content-Length: 0` (request) | FE 가 body 없이 호출. `@Valid @RequestBody` 가 빈 본문 거부 → 정상 동작. body 스펙 확인 후 채워서 보내도록 안내 |
| 매핑 안 된 경로 → 500 | catch-all 핸들러가 `Exception` 잡음. 4xx 분기는 GlobalExceptionHandler 의 `NoResourceFoundException` / `HttpRequestMethodNotSupportedException` 처리됨 — 이미 적용 |
| Quiz 가 PUBLIC 으로 저장됨 | 빌더 디폴트 PRIVATE. `visibility` 명시 안 하면 PRIVATE — 의도된 동작. PUBLIC 원하면 `.visibility(QuizVisibility.PUBLIC)` 명시 |
| 응답 DTO record 생성자 길이 변경으로 테스트 컴파일 에러 | record 는 fail-fast. 모든 사용처 일괄 수정. positional argument 는 신규 필드 위치 주의 |
| Flyway "checksum mismatch" | 이미 적용된 V 파일을 수정하면 발생. 새 V 파일로 ALTER 추가 |
| 테스트는 그린인데 prod 에서 500 | 마이그레이션 누락 가능. `flyway_schema_history` 확인 |
| presigned PUT 403 | `requiredHeaders` 모두 부착했는지, `Content-Type` 이 발급 시점과 동일한지 확인 |
| `@AuthenticationPrincipal` NPE | SecurityConfig 가 permitAll 인 경로면 nullable. null 체크 후 `userDetails.getUser().getId()` |
| 페이지네이션이 무시됨 | `?sort=field,desc` Spring 기본 형식은 미사용. `QuizSort` enum 의 키 (`latest`/`plays`/...) 사용 |
