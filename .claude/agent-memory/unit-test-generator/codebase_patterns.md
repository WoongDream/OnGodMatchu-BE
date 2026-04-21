---
name: OnGodMatchu 테스트 코드 패턴
description: GradingService 테스트 및 일반 패턴 기록
type: reference
---

## 테스트 구조 패턴

### Service Layer 테스트
- `@ExtendWith(MockitoExtension.class)` - 경량 유닛 테스트
- `@InjectMocks`로 테스트 대상 주입
- `@Mock`으로 의존성 주입
- BDDMockito 스타일: `given()`, `then()` 사용

### 픽스처 헬퍼 메서드
```java
private Question testQuestion(String answer) {
  User user = User.builder()
      .email("u@example.com")
      .nickname("작성자")
      .provider(AuthProvider.LOCAL)
      .emailVerified(true)
      .build();
  Quiz quiz = Quiz.builder().user(user).title("퀴즈").category("게임").build();
  Question question = Question.builder().quiz(quiz).orderNum(1).answer(answer).build();
  ReflectionTestUtils.setField(question, "id", 1L);
  return question;
}
```
- Entity ID 설정은 `ReflectionTestUtils.setField()` 사용
- Builder 패턴 활용

## GradingService 테스트 케이스

### 완전 일치 (Exact Match)
1. **완전 일치 시 AI 호출 안 함**: 성능 최적화 확인
2. **대소문자 무시** (equalsIgnoreCase)
3. **앞뒤 공백 무시** (trim)
4. **조합**: 공백 + 대소문자 동시 처리

### AI 채점 통합
- Exact match 실패 → AI 서비스 호출
- AI 결과 true/false 그대로 반환
- Mock 검증으로 호출 여부 확인: `then(aiGradingService).should(never()).grade()`

### 에러 처리
- 존재하지 않는 Question ID → `BusinessException(QUESTION_NOT_FOUND)`
- 예외 검증: `isInstanceOf()`, `extracting()`, `isEqualTo()`

### 응답 검증
- GradeResponse 필드 검증: `questionId`, `correct`, `correctAnswer`

## 테스트명 패턴
- `grade_완전일치_AI호출없음()` 형식
- `@DisplayName`으로 한글 설명
- 메서드명은 영문 스네이크 케이스

## 어설션 라이브러리
- AssertJ: `assertThat()`, `assertThatThrownBy()`
- Mockito 검증: `then()`, `should()`, `never()`, `anyString()`
