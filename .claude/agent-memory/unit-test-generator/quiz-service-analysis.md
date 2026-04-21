---
name: QuizService 테스트 현황 분석
description: src/test/java/.../QuizServiceTest.java 현재 커버리지 및 개선 필요 영역
type: project
---

## 현재 테스트 커버리지 (5개 테스트)

### getQuizList (2개 테스트) ✓ 완벽
- `getQuizList_noCategory`: category 없이 조회 (null or empty string)
- `getQuizList_withCategory`: category 필터 적용

### getQuizDetail (2개 테스트) ✓ 거의 완벽
- `getQuizDetail_notFound`: 존재하지 않는 ID → BusinessException
- `getQuizDetail_success`: 정상 조회

### createQuiz (1개 테스트) ⚠️ 불완전
- `createQuiz_success`: 행복 경로만 (단일 질문)

### incrementPlayCount (1개 테스트) ✓ 충분
- `incrementPlayCount_success`: 카운트 증가 확인

## 개선 필요 영역

### getQuizList
- Edge case: 빈 category string (`""`) 테스트 필요?
- 페이지네이션: 2페이지 이상, 빈 결과 확인

### getQuizDetail
- Edge case: 존재하는 Quiz인데 Question이 없는 경우 (현재는 테스트하지만 확인할 가치)
- 여러 Question 포함된 경우 (orderNum 순서 확인)

### createQuiz
- 사용자가 없는 경우: USER_NOT_FOUND 예외
- 여러 개의 질문 생성 (orderNum 순서 검증)
- saveAll 호출 횟수 검증 (질문 개수 만큼)

### incrementPlayCount
- Quiz가 없는 경우: QUIZ_NOT_FOUND 예외

## 결론
**현재 테스트가 거의 완벽하나, createQuiz에 2개, incrementPlayCount에 1개 에러 케이스 추가 권장.**
