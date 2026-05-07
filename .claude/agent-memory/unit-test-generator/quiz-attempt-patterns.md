---
name: QuizAttempt 테스트 패턴
description: QuizAttemptService/Controller 테스트 핵심 패턴 — submit 채점 로직, page size cap, visibility 분기, Mockito eq import 누락 문제
type: project
---

## QuizAttemptServiceTest 패턴 (28개 케이스)

### submit() 핵심 stubbing
```java
given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of(q));
given(userRepository.findById(1L)).willReturn(Optional.of(owner));
given(quizAttemptRepository.save(any(QuizAttempt.class))).willAnswer(inv -> {
    ReflectionTestUtils.setField((QuizAttempt) inv.getArgument(0), "id", 100L);
    return inv.getArgument(0);
});
```

### submit() AI fallback 패턴
- exactMatch 성공 시 aiGradingService.grade() 절대 호출 안 됨 → `then(aiGradingService).should(never()).grade(...)`
- exactMatch 실패 후 AI fallback → `given(aiGradingService.grade("수도", "서울")).willReturn(true)`

### testAttempt 픽스처 — completedAt은 @PrePersist로 설정되므로 직접 reflection 필요
```java
QuizAttempt attempt = QuizAttempt.builder()...build();
ReflectionTestUtils.setField(attempt, "id", 100L);
ReflectionTestUtils.setField(attempt, "completedAt", LocalDateTime.of(2025, 5, 1, 12, 0));
```

### page size cap 검증
```java
quizAttemptService.getMyAttempts(1L, PageRequest.of(0, 200));
ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
then(quizAttemptRepository).should().findByUserIdOrderByCompletedAtDesc(eq(1L), captor.capture());
assertThat(captor.getValue().getPageSize()).isEqualTo(50);
```

### getAttemptsByPublicId 비공개 프로필 분기
- 비공개 + 외부뷰어(비로그인 포함) → `Page.empty()` 반환, `findByUserIdOrderByCompletedAtDesc` 호출 안 됨
- 비공개 + 본인(viewerUserId == author.getId()) → 정상 반환

## Spotless가 eq import를 제거하는 문제
Spotless(Google Java Format)가 미사용 import로 판단해 `eq` static import를 제거할 수 있음.
→ 실제 사용 여부와 무관하게 컴파일 오류 발생. `import static org.mockito.ArgumentMatchers.eq;` 수동 확인 필요.

## QuizAttemptControllerTest (9개 케이스)
- 비로그인 submit: `SecurityContextHolder.clearContext()` 후 `isNull()` matcher 사용
  ```java
  given(quizAttemptService.submit(eq(1L), isNull(), any(AttemptCreateRequest.class))).willReturn(response);
  ```
- 201 Created 상태코드 (200 아님) — `status().isCreated()`
- `attemptId` null 검증: `jsonPath("$.data.attemptId").isEmpty()`

## QuizServiceTest 보강 (getProfileStats — 5개 신규 케이스)
- `countWeeklyPlaysOfQuizzesOwnedBy` 와 `perQuizCorrectRatesOwnedBy` 모두 stub 필요
- perQuizRates 비어있으면 avgCorrectRate=null
- 복수 퀴즈 평균: Java Stream `average()` 결과 → 단순 산술 평균

## QuizServiceTest 보강 (mapToListItems/correctRate — 3개 신규 케이스)
- `QuizCorrectRateRow` 익명 구현으로 stub:
  ```java
  QuizAttemptRepository.QuizCorrectRateRow rateRow = new QuizAttemptRepository.QuizCorrectRateRow() {
      @Override public Long getQuizId() { return 1L; }
      @Override public Double getRate() { return 75.0; }
  };
  given(quizAttemptRepository.correctRateByQuizIds(List.of(1L))).willReturn(List.of(rateRow));
  ```
- 퀴즈 없으면 correctRateByQuizIds 아예 미호출 (빈 페이지 조기 종료)

**Why:** 신규 퀴즈 풀이 기록 기능 단위 테스트 작성 시 패턴 재사용을 위해 기록.
**How to apply:** 다음 attempt/submit 관련 테스트 작성 시 이 파일의 stubbing 패턴을 먼저 참조.
