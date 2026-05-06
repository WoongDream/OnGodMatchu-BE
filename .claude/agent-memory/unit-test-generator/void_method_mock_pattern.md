---
name: void 메서드 mock 패턴
description: JpaRepository의 flush() 같은 void 메서드에 예외를 stubbing하는 BDD 방식
type: feedback
---

`JpaRepository.flush()`는 void 반환 타입이므로 `given(repo.flush()).willThrow(...)` 는 컴파일 에러가 난다.

**올바른 BDD 방식:**
```java
willThrow(dive).given(userRepository).flush();
```

**Why:** `given()` 첫 번째 인자가 void 반환값을 받을 수 없으므로 `willThrow().given().method()` 체인을 사용해야 한다.

**How to apply:** 모든 void 메서드(save, flush, deleteBy*, update* 등)에 예외 stubbing 시 동일 패턴 적용.
