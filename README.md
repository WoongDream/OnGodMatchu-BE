# OnGodMatchu — Backend

이미지/텍스트 기반 주관식 퀴즈를 만들고 공유하는 서비스의 백엔드 서버.

> 프론트엔드: [WoongDream/OnGodMatchu-FE](https://github.com/WoongDream/OnGodMatchu-FE)  
> 운영 도메인: [api.ongodmatchu.com](https://api.ongodmatchu.com)  
> 배포 가이드: [DEPLOYMENT.md](./DEPLOYMENT.md)

## 기술 스택

| 영역 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| ORM | Spring Data JPA + QueryDSL |
| Security | Spring Security + JWT |
| OAuth2 | Google / Naver / Kakao |
| Database | PostgreSQL |
| Cache | Redis |
| Storage | AWS S3 |
| Mail | Spring Mail (Gmail SMTP) |
| AI | Claude API (주관식 채점) |
| Build | Gradle |
| Docs | SpringDoc (Swagger UI) |
| Formatter | Spotless (Google Java Format) |
| Deploy | AWS EC2 / ECS + RDS + ElastiCache |

## 화면 구조 (API 대응)

```
GET  /api/quizzes            → 퀴즈 목록 (카테고리 필터 + 페이지네이션)
GET  /api/quizzes/:id        → 퀴즈 상세 + 문제 목록
POST /api/quizzes            → 퀴즈 생성
POST /api/quizzes/:id/play   → 플레이 카운트 증가
POST /api/quizzes/grade      → 주관식 정답 채점 (AI)

POST /api/auth/signup        → 이메일 회원가입
POST /api/auth/verify-email  → 이메일 인증
POST /api/auth/login         → 로그인
POST /api/auth/refresh       → 토큰 재발급
POST /api/auth/logout        → 로그아웃
GET  /oauth2/authorization/* → 소셜 로그인 (Google / Naver / Kakao)

POST /api/upload/presigned   → S3 Presigned URL 발급
```

## 패키지 구조

```
src/main/java/com/ongodmatchu/
├── domain/
│   ├── user/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── quiz/
│   ├── question/
│   └── auth/
├── global/
│   ├── config/        # Security, CORS, S3 등 설정
│   ├── exception/     # GlobalExceptionHandler, 커스텀 예외
│   ├── response/      # ApiResponse<T> 공통 포맷
│   └── util/
└── infra/
    ├── s3/
    ├── mail/
    └── ai/
```

## 환경 변수

로컬 실행 시 `application-local.yml` 또는 환경 변수로 주입한다.

| 변수 | 설명 |
|------|------|
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME` | DB 사용자명 |
| `DB_PASSWORD` | DB 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 (256bit 이상) |
| `MAIL_USERNAME` | Gmail 계정 |
| `MAIL_PASSWORD` | Gmail 앱 비밀번호 |
| `GOOGLE_CLIENT_ID` | Google OAuth2 클라이언트 ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 클라이언트 Secret |
| `NAVER_CLIENT_ID` | Naver OAuth2 클라이언트 ID |
| `NAVER_CLIENT_SECRET` | Naver OAuth2 클라이언트 Secret |
| `KAKAO_CLIENT_ID` | Kakao OAuth2 클라이언트 ID |
| `KAKAO_CLIENT_SECRET` | Kakao OAuth2 클라이언트 Secret |
| `AWS_ACCESS_KEY` | AWS IAM Access Key |
| `AWS_SECRET_KEY` | AWS IAM Secret Key |
| `AWS_S3_BUCKET` | S3 버킷 이름 |
| `CLAUDE_API_KEY` | Anthropic Claude API Key |
| `FRONTEND_URL` | 프론트엔드 URL (CORS) |

## 주요 명령어

```bash
./gradlew bootRun                  # 개발 서버 실행
./gradlew test                     # 테스트 실행
./gradlew spotlessCheck            # 포맷 검사
./gradlew spotlessApply            # 자동 포맷
./gradlew build                    # 프로덕션 빌드
```

## API 문서

서버 실행 후 [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) 접속

## 공통 응답 포맷

```json
// 성공
{ "success": true, "data": { ... } }

// 실패
{ "success": false, "error": { "code": "QUIZ_NOT_FOUND", "message": "퀴즈를 찾을 수 없습니다." } }
```

## 컨벤션

[CONVENTIONS.md](./CONVENTIONS.md) 참고
