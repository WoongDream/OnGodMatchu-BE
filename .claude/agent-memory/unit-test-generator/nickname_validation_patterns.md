---
name: NicknameNormalizer/NicknamePolicy 의존성 패턴
description: UserService/AuthService에 NicknameNormalizer·NicknamePolicy가 추가되어 기존 테스트에도 mock이 필요
type: project
---

`UserService`와 `AuthService` 모두 `NicknameNormalizer`, `NicknamePolicy`를 생성자 주입으로 받는다.

기존 Mockito 단위 테스트(`@InjectMocks`)에 이 두 mock이 없으면 `updateMe`, `signup`, `checkNicknameAvailability` 흐름이 null을 받아 NPE 또는 예상치 못한 동작을 한다.

**Why:** `nicknameNormalizer.normalize()`는 mock 기본값으로 null을 반환하므로 `nicknamePolicy.enforce(null)`이 호출되어 `BusinessException`이 터진다.

**How to apply:** `UserServiceTest`와 `AuthServiceTest` 양쪽에 `@Mock NicknameNormalizer nicknameNormalizer`와 `@Mock NicknamePolicy nicknamePolicy`를 선언하고, `updateMe`/`signup` 테스트마다 `given(nicknameNormalizer.normalize("닉네임")).willReturn("닉네임")` stub을 설정한다.
