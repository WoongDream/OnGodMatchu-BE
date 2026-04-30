---
name: WebMvcTest JPA Auditing 충돌 패턴
description: @WebMvcTest 슬라이스에서 @EnableJpaAuditing이 메인 클래스에 있을 때 발생하는 컨텍스트 로드 실패 해결법
type: feedback
---

`@EnableJpaAuditing`이 `OngodmatchuApplication.java`에 직접 붙어 있기 때문에 `@WebMvcTest` 슬라이스가 `jpaAuditingHandler`를 빈으로 등록하려다 `jpaMappingContext`를 찾지 못해 `BeanCreationException`으로 실패한다.

해결: 테스트 클래스에 `@MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;` 추가.

**Why:** `@WebMvcTest`는 JPA 레이어를 로드하지 않으므로 JPA auditing이 의존하는 매핑 컨텍스트가 없다.

**How to apply:** `@WebMvcTest`를 쓰는 모든 Controller 테스트에 이 `@MockitoBean` 선언을 반드시 포함한다.
