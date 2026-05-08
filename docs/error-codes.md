# ErrorCode 카탈로그

> 응답 본문은 `ApiResponse` 포맷:
> ```json
> { "success": false, "error": { "code": "QUIZ_NOT_FOUND", "message": "퀴즈를 찾을 수 없습니다." } }
> ```
> `RATE_LIMITED` 만 추가 헤더 `Retry-After` (초) 와 본문에 `error.retryAfter` 포함.
>
> 단일 진실 원천: `src/main/java/com/ongodmatchu/global/exception/ErrorCode.java`. 표는 보조 자료이며 코드를 우선한다.

---

## Common

| Code | Status | 의미 / 발생 |
|---|---|---|
| `INVALID_INPUT` | 400 | `@Valid` 본문 검증 실패 / `HttpMessageNotReadableException`(잘못된 JSON) |
| `UNAUTHORIZED` | 401 | 인증 필요 endpoint 에 토큰 없음/만료/위조 (Spring Security catch-all). FE: 토큰 갱신 시도 → 실패 시 재로그인 |

## Routing (HTTP)

매핑/메서드 오류는 `BusinessException` 이 아니지만 동일 `ApiResponse` 포맷으로 응답.

| Code | Status | 의미 |
|---|---|---|
| `NOT_FOUND` | 404 | 매핑되지 않은 경로 (`NoResourceFoundException`) |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드 |
| `INTERNAL_SERVER_ERROR` | 500 | 미처리 예외 (서버 로그에 stack 기록됨) |

## User / Auth (`/api/auth/*`, `/api/users/me`, `/api/users/me/password`)

| Code | Status | 발생 |
|---|---|---|
| `USER_NOT_FOUND` | 404 | `findById` / `findByPublicId` 실패 |
| `EMAIL_ALREADY_EXISTS` | 409 | `POST /signup` (이메일 중복) |
| `NICKNAME_ALREADY_EXISTS` | 409 | `POST /signup` / `PATCH /me` |
| `INVALID_NICKNAME_FORMAT` | 400 | `NicknamePolicy` 위반 (길이/문자) |
| `INVALID_BIO_FORMAT` | 400 | `BioPolicy` 위반 (길이) |
| `INVALID_PASSWORD` | 401 | `POST /login` (비밀번호 불일치) |
| `INVALID_CURRENT_PASSWORD` | 401 | `PATCH /me/password` (현재 비밀번호 불일치) |
| `OAUTH_USER_NO_PASSWORD` | 400 | OAuth 가입자가 비밀번호 변경/로그인 시도 |
| `EMAIL_NOT_VERIFIED` | 403 | `POST /login` (이메일 미인증) |
| `SOCIAL_USER_PASSWORD_LOGIN` | 400 | 소셜 가입자가 비밀번호 로그인 시도 |
| `PASSWORD_POLICY_VIOLATION` | 400 | NIST 정책 위반 (길이/공백/이메일·닉네임 일치) |
| `PASSWORD_BREACHED` | 422 | HIBP k-anonymity 유출 검증 실패 |

## Email Verification (`POST /api/auth/send-verification-code`, `POST /signup`)

| Code | Status | 발생 |
|---|---|---|
| `INVALID_VERIFICATION_CODE` | 400 | 코드 불일치 |
| `VERIFICATION_CODE_EXPIRED` | 400 | 코드 만료 |
| `RATE_LIMITED` | 429 | `VerificationCodeRateLimiter` 초과. 본문 `error.retryAfter` (초) + `Retry-After` 헤더 |

## Token (`/api/auth/refresh`, `/api/auth/logout`)

| Code | Status | 발생 |
|---|---|---|
| `INVALID_TOKEN` | 401 | refresh 토큰 위조/형식 오류 |
| `TOKEN_EXPIRED` | 401 | refresh 토큰 만료 → 재로그인 필요 |
| `REFRESH_TOKEN_NOT_FOUND` | 401 | DB 에 RT 없음 (이미 무효화됨) → 재로그인 필요 |

## Quiz (`/api/quizzes`, `/api/users/me/quizzes`)

| Code | Status | 발생 |
|---|---|---|
| `QUIZ_NOT_FOUND` | 404 | 존재하지 않는 퀴즈 / **PRIVATE 퀴즈에 외부 뷰어 접근**(정보 노출 최소화) |
| `QUIZ_FORBIDDEN` | 403 | `PATCH`/`DELETE /api/quizzes/{id}` — 본인 아님 |
| `INVALID_CATEGORY` | 400 | 카테고리 화이트리스트 외 키 |

## Question

| Code | Status | 발생 |
|---|---|---|
| `QUESTION_NOT_FOUND` | 404 | 채점 등에서 문제 미존재 |

## Comment (`/api/quizzes/{id}/comments`, `/api/comments/{id}`)

| Code | Status | 발생 |
|---|---|---|
| `COMMENT_NOT_FOUND` | 404 | 존재하지 않거나 soft-deleted |
| `COMMENT_FORBIDDEN` | 403 | 본인이 아닌 댓글 수정/삭제 시도 |
| `INVALID_COMMENT_FORMAT` | 400 | `CommentPolicy` 위반 (빈 내용 / 500자 초과) |

## Upload (`/api/upload/*`, `/api/users/me/profile-image`)

| Code | Status | 발생 |
|---|---|---|
| `INVALID_FILE_TYPE` | 400 | `image/jpeg`/`png`/`webp` 외 |
| `INVALID_FILE_SIZE` | 400 | 퀴즈 5MB / 프로필 3MB 초과 |
| `UPLOAD_NOT_FOUND` | 404 | `/complete` 시 메타 없음 |
| `UPLOAD_FORBIDDEN` | 403 | 본인이 발급하지 않은 key 로 `/complete` |
| `UPLOAD_VERIFICATION_FAILED` | 422 | S3 HEAD 검증 실패 (파일 미존재 등) |
| `INVALID_UPLOAD_KEY` | 400 | 잘못된 prefix / `verifyKeyOwnedAndCompleted` 실패 |

---

## FE 처리 권장 패턴

```ts
// 401 → 토큰 갱신 시도. UNAUTHORIZED / INVALID_TOKEN / TOKEN_EXPIRED / REFRESH_TOKEN_NOT_FOUND 모두 동일하게 처리
// 403 → 사용자 안내 ("권한 없음")
// 404 → 페이지/목록 갱신 (없는 리소스)
// 409 → 폼 인라인 에러 (중복)
// 422 → 비즈니스 위반 (HIBP / S3 검증)
// 429 → error.retryAfter 사용해 안내 + 카운트다운
```
