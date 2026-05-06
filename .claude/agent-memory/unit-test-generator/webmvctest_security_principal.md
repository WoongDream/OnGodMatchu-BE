---
name: WebMvcTest @AuthenticationPrincipal 주입 패턴
description: addFilters=false 환경에서 @AuthenticationPrincipal을 주입하는 올바른 방법
type: feedback
---

`@WebMvcTest` + `@AutoConfigureMockMvc(addFilters = false)` 조합에서 `@AuthenticationPrincipal`이 null이 되는 문제.

`SecurityMockMvcRequestPostProcessors.authentication()` 또는 `user()` post processor가 Security filter chain이 없으면 SecurityContext를 전달하지 못해 controller에서 NPE 발생.

**해결: `@BeforeEach`에서 SecurityContextHolder를 직접 세팅하고 `@AfterEach`에서 정리한다.**

```java
@BeforeEach
void setUp() {
    CustomUserDetails userDetails = new CustomUserDetails(testUser);
    Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
}

@AfterEach
void tearDown() {
    SecurityContextHolder.clearContext();
}
```

비로그인 테스트(viewerUserId=null)는 해당 테스트 메서드에서 `SecurityContextHolder.clearContext()` 호출.

**Why:** Spring Security 6 + `addFilters=false`에서 `SecurityContextHolderFilter`가 비활성화되어 post processor가 세팅한 SecurityContext가 Resolver에 전달되지 않는다.

**How to apply:** `UserController`처럼 `@AuthenticationPrincipal`을 사용하는 Controller 슬라이스 테스트에 항상 적용.
