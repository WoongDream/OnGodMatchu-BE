---
name: Test 패턴 및 관례
description: 기존 테스트 코드에서 확인된 Mockito/JUnit5 패턴 및 프로젝트 컨벤션
type: reference
---

## 프로젝트 테스트 구조
- `@ExtendWith(MockitoExtension.class)` 사용 (순수 유닛 테스트, Spring context 불필요)
- `@Mock` + `@InjectMocks` 패턴
- BDDMockito 스타일: `given(...).willReturn(...)` + `then(...).should(...)`

## 엔티티 ID 설정 패턴
```java
private User testUser() {
    User user = User.builder()
        .email("user@example.com")
        .nickname("작성자")
        .provider(AuthProvider.LOCAL)
        .emailVerified(true)
        .build();
    ReflectionTestUtils.setField(user, "id", 1L);  // ID 설정
    return user;
}
```

## 검증 스타일
- AssertJ: `assertThat(...).isEqualTo(...)`
- 예외: `assertThatThrownBy(() -> ...).isInstanceOf(BusinessException.class).extracting(...)`

## Mock 저장소 반환 패턴
```java
given(quizRepository.save(any())).willAnswer(inv -> inv.getArgument(0));  // save 직후 Entity 반환
given(questionRepository.findByQuizIdOrderByOrderNum(1L)).willReturn(List.of());  // findBy 메서드
```

## 메서드 호출 검증
```java
then(questionRepository).should().save(any());  // 특정 메서드 호출 여부 확인
```

## 테스트 클래스 레이아웃
1. 클래스 선언 + 인젝션 필드
2. 픽스처 빌더 메서드 (testUser(), testQuiz() 등)
3. 테스트 메서드들 (`@DisplayName` 한국어 설명)
