# 개발 컨벤션

## 가장 중요

Spotless (Google Java Format) 설정을 준수한다.

```bash
./gradlew spotlessCheck   # 포맷 검사
./gradlew spotlessApply   # 자동 포맷
```

---

## 패키지 구조

도메인 중심으로 패키지를 구성한다.

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
├── global/                  ← 전역 공통
│   ├── config/              # Security, CORS, S3 등 설정
│   ├── exception/           # GlobalExceptionHandler, 커스텀 예외
│   ├── response/            # ApiResponse<T> 공통 포맷
│   └── util/
└── infra/                   ← 외부 인프라 연동
    ├── s3/
    ├── mail/
    └── ai/
```

---

## 명명 규칙

| 대상 | 규칙 | 예시 |
|------|------|------|
| 패키지 | lowercase | `com.ongodmatchu.domain.quiz` |
| 클래스 | PascalCase | `QuizController` |
| 메서드 / 변수 | camelCase | `findQuizById` |
| 상수 | UPPER_SNAKE_CASE | `MAX_QUESTION_COUNT` |
| DB 컬럼 | snake_case | `created_at` |
| 요청 DTO | `XxxRequest` | `QuizCreateRequest` |
| 응답 DTO | `XxxResponse` | `QuizResponse` |
| 예외 클래스 | `XxxException` | `QuizNotFoundException` |

---

## 코드 스타일

### 의존성 주입

필드 주입 금지, 생성자 주입만 사용한다. (`@RequiredArgsConstructor` 활용)

```java
// X
@Autowired
private QuizService quizService;

// O
@RequiredArgsConstructor
public class QuizController {
    private final QuizService quizService;
}
```

### Entity / DTO 분리

Entity를 컨트롤러에서 직접 반환하지 않는다.

```java
// X
return quizRepository.findById(id);

// O
Quiz quiz = quizService.findById(id);
return QuizResponse.from(quiz);
```

### Optional 활용

```java
// X
Quiz quiz = quizRepository.findById(id).get();

// O
Quiz quiz = quizRepository.findById(id)
        .orElseThrow(QuizNotFoundException::new);
```

### 레이어 책임

- `Controller` → 요청/응답만, 비즈니스 로직 없음
- `Service` → 비즈니스 로직, 트랜잭션 관리
- `Repository` → DB 쿼리만

### 트랜잭션

- Service 메서드에 `@Transactional` 명시
- 조회 전용 메서드는 `@Transactional(readOnly = true)` 사용

```java
@Transactional(readOnly = true)
public QuizResponse findById(Long id) { ... }

@Transactional
public QuizResponse create(QuizCreateRequest request) { ... }
```

---

## 공통 응답 포맷

`ApiResponse<T>` 래퍼로 통일한다.

```json
// 성공
{ "success": true, "data": { ... } }

// 실패
{ "success": false, "error": { "code": "QUIZ_NOT_FOUND", "message": "퀴즈를 찾을 수 없습니다." } }
```

---

## 예외 처리

`GlobalExceptionHandler` (`@RestControllerAdvice`) 로 전역 통일한다.
커스텀 예외는 `global/exception/` 아래에서 관리한다.

---

## 커밋 메시지

### 형식

```
[type] 변경 내용 요약
```

### 타입 정의

| 타입 | 설명 |
|------|------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 구조 개선 |
| `style` | 포맷, 세미콜론 등 코드 스타일 변경 |
| `docs` | 문서 추가/수정 |
| `chore` | 의존성 추가, 설정 파일 등 기타 |
| `remove` | 파일/코드 삭제 |

### 예시

```
[feat] 퀴즈 목록 조회 API 구현
[fix] 정답 확인 시 null 반환되는 버그 수정
[chore] JWT 의존성 추가
[docs] CONVENTIONS.md 작성
[remove] 사용하지 않는 설정 파일 삭제
```
