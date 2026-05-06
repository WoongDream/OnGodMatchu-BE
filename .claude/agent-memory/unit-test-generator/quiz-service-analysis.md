---
name: QuizService 테스트 현황 분석
description: src/test/java/.../QuizServiceTest.java 현재 커버리지 — 39개 케이스 완성
type: project
---

## 현재 테스트 커버리지 (39개 테스트) — 2026-05-02 기준

### getQuizList (2개)
- noCategory / withCategory

### getQuizDetail (2개)
- notFound / success

### createQuiz (6개)
- success / persistsImageKeys / sameKeyForBoth / invalidCategory / userNotFound / multipleQuestions

### getCategories (1개)
- success (9개 카테고리 순서 검증)

### incrementPlayCount (2개)
- success / quizNotFound

### getMyQuizList (3개) — 2026-05-02 추가
- returnsPaginatedResults: findByUserIdOrderByCreatedAtDesc 호출 검증
- noQuizzes_returnsEmptyPage: 빈 페이지
- withThumbnailKey_mapsPresignedUrl: batchPresignViewUrls 매핑

### getQuizListByPublicId (6개) — 2026-05-02 추가
- userNotFound_throwsException: USER_NOT_FOUND
- publicProfile_anonymousViewer: viewerUserId=null + isProfilePublic=true → 목록 반환
- publicProfile_externalViewer: 외부 로그인 뷰어 + 공개 → 목록 반환
- privateProfile_owner: 비공개 + 본인(viewerUserId==author.id) → 목록 반환
- privateProfile_externalViewer: 비공개 + 외부 → 빈 페이지, repository 호출 never
- privateProfile_anonymousViewer: 비공개 + null → 빈 페이지, repository 호출 never

### updateQuiz (9개) — 2026-05-02 추가
- quizNotFound / notOwner(QUIZ_FORBIDDEN) / updateTitle / updateDescription
- updateCategory_validKey / invalidCategory(INVALID_CATEGORY)
- newThumbnailKey_verifiesAndDeletesPrevious: verify+deleteQuietly(oldKey) 호출
- newThumbnailKey_noPreviousKey: verify 호출, deleteQuietly never
- sameThumbnailKey: verify/delete 둘 다 never
- allNullFields: 변경 없음 확인

### deleteQuiz (6개) — 2026-05-02 추가
- quizNotFound / notOwner(QUIZ_FORBIDDEN)
- success_deletesQuestionsAndQuiz: deleteByQuizId + delete 호출
- withThumbnail_deletesS3Object
- questionWithDifferentImageKeys_deletesBoth: imageKey/answerImageKey 각각 deleteQuietly
- questionWithSameImageAndAnswerKey_deletesOnce: times(1) 검증
- questionWithNoImageKeys_noS3Delete: never() 검증

## 핵심 패턴
- `User.updateProfilePublic(false)` 로 비공개 유저 fixture 생성
- `ReflectionTestUtils.setField(quiz, "thumbnailKey", "...")` 로 thumbnailKey 주입
- `willDoNothing().given(s3Service).deleteQuietly(...)` 로 void 메서드 stubbing
- `then(repo).should(never()).method(...)` 로 미호출 검증
