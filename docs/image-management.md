# Image Management Guide

OnGodMatchu 의 이미지 저장 / 서빙 아키텍처.

> S3 버킷은 **private + ACL 비활성화**. DB 에는 **S3 key 만 저장**하고, 응답 시점에 동적으로 GET signed URL 을 생성해 내려준다.

## 아키텍처 개요

```
┌──────────┐  ① POST /api/upload/presigned       ┌──────────┐
│          │ ───────────────────────────────────> │          │
│    FE    │  ② PUT (presigned, 10분, Content-    │    BE    │
│          │     Type 서명)         ┌─────────┐   │          │
│          │ ──────────────────────>│   S3    │   │          │
│          │                        └─────────┘   │          │
│          │  ③ PATCH /api/upload/complete        │          │
│          │ ───────────────────────────────────> │          │
│          │                                       │ HEAD obj │
│          │                                       │ → check  │
│          │  ④ POST /api/quizzes (key 만 전송)    │ size/CT  │
│          │ ───────────────────────────────────> │          │
│          │  ⑤ GET /api/quizzes/:id              │          │
│          │     (응답에 동적 viewUrl 포함)        │          │
│          │ <─────────────────────────────────── │          │
└──────────┘                                       └──────────┘
```

| 단계 | 동작 |
|------|------|
| ① | BE가 `quiz-images/{user.publicId}/{uuid}.{ext}` key + presigned PUT URL 발급, `upload_meta` 에 PENDING 레코드 INSERT |
| ② | FE가 직접 S3에 PUT (BE 트래픽 없음). Content-Type 이 서명에 포함되어 있어 다른 타입 PUT은 S3가 거부 |
| ③ | FE가 PATCH `/complete` 호출 → BE가 S3 HEAD 로 size / contentType 재검증 후 `upload_meta` 를 COMPLETED 로 전환 |
| ④ | 퀴즈 생성 시 FE는 **key 만** 전송. BE는 모든 key 가 (a) 본인 소유 (b) COMPLETED 상태인지 검증 |
| ⑤ | 조회 응답을 만들 때 BE가 모든 key 를 모아 한 번에 GET signed URL (1시간 유효) 로 변환해 내려줌 |

## DB 저장 정책 — key 만 저장

DB 에 만료되는 URL 을 저장하면 시간 경과 후 모든 이미지가 깨진다. 정책을 하나로 통일.

| 컬럼 | 형태 | 비고 |
|------|------|------|
| `quizzes.thumbnail_key` | `VARCHAR(500)` | nullable. 예: `quiz-images/{publicId}/{uuid}.jpg` |
| `questions.image_key` | `VARCHAR(500)` | nullable. 문제 이미지 |
| `questions.answer_image_key` | `VARCHAR(500)` | nullable. 정답 이미지 |
| `users.public_id` | `UUID` | 외부 노출 식별자 (S3 key 에 사용) |
| `quizzes.public_id` | `UUID` | 외부 노출 식별자 |

`User.id` / `Quiz.id` 는 BIGSERIAL 이라 enumeration 공격 위험이 있어, S3 key·외부 URL 노출용으로 `public_id UUID` 컬럼을 별도로 운영한다. 내부 PK/FK 는 `id` 그대로 사용.

## API

### `POST /api/upload/presigned` — 업로드용 presigned URL 발급

**인증 필요.** 응답 받은 `uploadUrl` 로 클라이언트가 직접 S3 에 PUT 한다.

```http
POST /api/upload/presigned
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "filename": "photo.jpg",
  "contentType": "image/jpeg",
  "sizeBytes": 1234567
}
```

```json
{
  "success": true,
  "data": {
    "uploadUrl": "https://...?X-Amz-Signature=...",
    "key": "quiz-images/550e8400-.../abc123.jpg",
    "expiresIn": 600
  }
}
```

- `uploadUrl` 은 **10분** 유효. 그 안에 PUT 완료해야 한다.
- 발급 시 BE에서 1차 검증 — 허용 contentType / size 위반 시 `400 INVALID_FILE_TYPE` / `INVALID_FILE_SIZE`.
- 발급과 동시에 `upload_meta` 에 PENDING 레코드 INSERT.

### S3 PUT — 클라이언트가 직접 업로드

```http
PUT <uploadUrl>
Content-Type: image/jpeg

<binary>
```

- presigned URL 에 Content-Type 이 서명되어 있어 발급 시 명시한 값과 다르면 S3 가 거부.
- BE 를 거치지 않고 S3 가 직접 받는다.

### `PATCH /api/upload/complete` — 업로드 완료 알림

**인증 필요.** PUT 성공 후 호출하면 BE 가 S3 HEAD 로 실제 객체를 검증하고 `upload_meta` 를 COMPLETED 로 전환한다.

```http
PATCH /api/upload/complete
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "key": "quiz-images/550e8400-.../abc123.jpg" }
```

- 본인 메타가 아니면 `403 UPLOAD_FORBIDDEN`
- S3 에 객체 없음 → `422 UPLOAD_VERIFICATION_FAILED`
- HEAD 결과의 contentType / size 가 정책 위반 → `400 INVALID_FILE_TYPE` / `INVALID_FILE_SIZE`

### `GET /api/upload/signed?key=...` — GET signed URL 발급

**공개 엔드포인트.** 단독 이미지 미리보기 등 퀴즈 컨텍스트 외에서 이미지 GET 이 필요할 때 사용.

```http
GET /api/upload/signed?key=quiz-images/550e8400-.../abc123.jpg
```

```json
{
  "success": true,
  "data": {
    "viewUrl": "https://...?X-Amz-Signature=...",
    "key": "quiz-images/550e8400-.../abc123.jpg",
    "expiresIn": 3600,
    "expiresAt": "2026-05-02T10:00:00Z"
  }
}
```

- `viewUrl` 은 **1시간** 유효.
- 일반 퀴즈 조회 응답에는 이미 동적 `viewUrl` 이 포함돼 있어 이 엔드포인트가 별도로 필요하지 않다.

### 퀴즈 응답에 포함되는 viewUrl

`GET /api/quizzes/:id` 등 퀴즈 응답 DTO 는 `*Key` 와 `*Url` 를 함께 내려준다.

```json
{
  "id": 123,
  "publicId": "550e8400-...",
  "title": "...",
  "thumbnailKey": "quiz-images/550e8400-.../thumb.jpg",
  "thumbnailUrl": "https://...?X-Amz-Signature=...",
  "questions": [
    {
      "id": 1,
      "imageKey": "quiz-images/550e8400-.../q1.jpg",
      "imageUrl": "https://...?X-Amz-Signature=...",
      "answerImageKey": "quiz-images/550e8400-.../a1.jpg",
      "answerImageUrl": "https://...?X-Amz-Signature=..."
    }
  ]
}
```

응답 생성 시 모든 key 를 한 번에 모아 `S3Service.batchPresignViewUrls` 로 일괄 변환 (N+1 회피). FE 는 보통 `*Url` 만 사용하면 되며, `*Key` 는 디버깅·리업로드 등을 위해 함께 노출된다.

## 파일 제한 정책 — 3중 검증

| 단계 | 위치 | 검증 |
|------|------|------|
| 1차 | FE | UX 즉시 피드백 (확장자 / size) |
| 2차 | BE — `POST /api/upload/presigned` | `contentType` 화이트리스트 + `sizeBytes` 5MB 이하 |
| 3차 | BE — `PATCH /api/upload/complete` | S3 HeadObject 로 실제 contentType / contentLength 재검증 |

| 항목 | 값 |
|------|------|
| 허용 contentType | `image/jpeg`, `image/png`, `image/webp` |
| 허용 확장자 | `.jpg`, `.jpeg`, `.png`, `.webp` |
| 최대 크기 | **5 MB** |
| presigned PUT TTL | **10분** |
| presigned GET TTL | **1시간** |

정책 정의 위치: [`UploadPolicy.java`](../src/main/java/com/ongodmatchu/infra/s3/UploadPolicy.java)

## S3 Key 네이밍

```
quiz-images/{user.publicId}/{uuid}.{ext}
```

예:
```
quiz-images/550e8400-e29b-41d4-a716-446655440000/abc123def-....jpg
```

- 모든 퀴즈 관련 이미지 (썸네일 / 문제 / 정답) 는 동일한 `quiz-images/` prefix 아래 저장. 추가 폴더 분기는 없음.
- `user.publicId` 는 외부 노출용 UUID. `User.id` (BIGSERIAL) 직접 노출 X.
- 파일명은 UUID v4. 원본 파일명은 `upload_meta.original_name` 에 별도 보관.

## 퀴즈 생성 시 key 검증

`POST /api/quizzes` 에서 받은 모든 key (`thumbnailKey`, 각 문제의 `imageKey` / `answerImageKey`) 는 다음을 만족해야 한다.

1. `upload_meta` 에 존재 → 없으면 `400 INVALID_UPLOAD_KEY`
2. 메타의 `user_id` 가 요청자와 동일 → 다르면 `403 UPLOAD_FORBIDDEN`
3. 메타 `status` 가 `COMPLETED` → 아니면 `422 UPLOAD_VERIFICATION_FAILED`

같은 key 를 여러 슬롯에 (예: `imageKey == answerImageKey`) 보내는 것은 허용되며, 검증은 1회만 수행한다.

## upload_meta 테이블

```
upload_meta
├── id (PK, BIGSERIAL)
├── user_id (FK → users.id)
├── s3_key (UNIQUE)
├── original_name
├── content_type
├── size_bytes
├── status (PENDING | COMPLETED)
├── created_at
└── completed_at
```

| 상태 | 시점 |
|------|------|
| `PENDING` | `POST /api/upload/presigned` 시 INSERT |
| `COMPLETED` | `PATCH /api/upload/complete` 시 S3 HEAD 검증 통과 후 전환 |

미완료 PENDING 레코드는 S3 라이프사이클 룰로 24h 이상 경과 시 자동 정리 예정 (인프라 작업).

## 에러 코드

| HTTP | `code` | 발생 조건 |
|------|--------|-----------|
| 400 | `INVALID_FILE_TYPE` | 허용 외 contentType (1차/3차 검증) |
| 400 | `INVALID_FILE_SIZE` | 5MB 초과 (1차/3차 검증) |
| 400 | `INVALID_UPLOAD_KEY` | DB 에 없는 key 로 퀴즈 생성 시도 |
| 403 | `UPLOAD_FORBIDDEN` | 다른 사용자의 메타에 접근 시도 |
| 404 | `UPLOAD_NOT_FOUND` | `/complete` 호출 시 메타 없음 |
| 422 | `UPLOAD_VERIFICATION_FAILED` | S3 HEAD 실패 또는 PENDING 상태로 퀴즈 생성 시도 |

응답 본문은 [공통 포맷](../README.md#공통-응답-포맷)을 따른다.

## 운영 인프라 요건

- **버킷**: `ongodmatchu-bucket`, private, ACL 비활성화 (버킷 정책 기반)
- **CORS**: PUT + GET, FE 오리진 (`http://localhost:5173`, `https://ongodmatchu.com`) 허용, `ETag` expose
- **IAM** (EC2 인스턴스 Role): `s3:PutObject` / `s3:GetObject` / `s3:HeadObject`
- **라이프사이클**: 24h 이상 경과한 PENDING 객체 자동 삭제 (Orphan 방지)

## 트러블슈팅

### 업로드 PUT 이 403 으로 실패

- presigned 발급 시 명시한 `contentType` 과 실제 PUT Header 가 다른 경우. presigned URL 에 Content-Type 이 서명돼 있어 일치해야 한다.
- TTL (10분) 만료. 다시 발급 받아야 한다.

### `viewUrl` 로 GET 시 SignatureDoesNotMatch

- presigned GET URL 의 TTL (1시간) 이 만료됐거나 시계가 어긋난 환경. 응답 받은 그대로 사용하고, 만료가 가까우면 재요청.

### 퀴즈 생성 시 `INVALID_UPLOAD_KEY`

- 클라이언트가 PUT 만 하고 `PATCH /api/upload/complete` 를 호출하지 않은 경우. complete 호출 누락 여부 확인.

### 퀴즈 생성 시 `UPLOAD_VERIFICATION_FAILED`

- complete 까지 호출했으나 S3 객체가 사라졌거나, 메타가 PENDING 상태로 멈춰 있는 경우. `upload_meta` row 의 `status` / S3 객체 존재 여부 확인.

## 관련 코드

| 파일 | 역할 |
|------|------|
| [`S3Service.java`](../src/main/java/com/ongodmatchu/infra/s3/S3Service.java) | presigned PUT/GET 발급, HEAD 검증, batch presign |
| [`UploadController.java`](../src/main/java/com/ongodmatchu/infra/s3/UploadController.java) | `/api/upload/*` 엔드포인트 |
| [`UploadPolicy.java`](../src/main/java/com/ongodmatchu/infra/s3/UploadPolicy.java) | 허용 contentType / size / 확장자 / key prefix |
| [`UploadMeta.java`](../src/main/java/com/ongodmatchu/infra/s3/UploadMeta.java) | PENDING/COMPLETED 추적 엔티티 |
| [`QuizService.java`](../src/main/java/com/ongodmatchu/domain/quiz/service/QuizService.java) | 퀴즈 생성 시 key 검증, 응답 시 batch presign |
