---
name: UserService applyProfileImage/deleteProfileImage s3Service stub 필요
description: applyProfileImage·deleteProfileImage 테스트에서 toResponse()가 generateViewUrl을 호출하므로 stub 필수
type: feedback
---

`UserService.applyProfileImage()`와 `deleteProfileImage()`는 내부적으로 `toResponse(user)`를 호출하고, `toResponse()`는 `resolveImageUrl(user)`를 통해 `s3Service.generateViewUrl(key).viewUrl()`을 호출한다.

profileImageKey가 null이 아닌 경우 stub 없으면 `generateViewUrl()` 반환값이 null → `.viewUrl()` 호출 시 NPE.

**올바른 패턴:**
```java
given(s3Service.generateViewUrl(key)).willReturn(viewUrlResponse(key));
```

`deleteProfileImage` 이후에는 `clearProfileImage()`로 key가 null이 되므로 `defaultProfileImageUrl`이 사용 → stub 불필요.

**Why:** `applyProfileImage` 성공 후 `toResponse`가 새 key로 presigned URL을 생성하므로 stub이 없으면 NPE.

**How to apply:** `applyProfileImage` 정상 케이스 테스트마다 새 key에 대한 `generateViewUrl` stub 추가.
