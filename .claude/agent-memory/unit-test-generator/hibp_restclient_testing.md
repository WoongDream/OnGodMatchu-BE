---
name: HibpClient RestClient 테스트 패턴
description: RestClient.Builder를 생성자에서 직접 소비하는 컴포넌트를 MockRestServiceServer로 테스트하는 방법
type: feedback
---

HibpClient는 생성자 안에서 `builder.requestFactory(simpleFactory).build()`를 호출하기 때문에, `MockRestServiceServer.bindTo(builder).build()` 이후 HibpClient를 생성하면 mock 팩토리가 overwrite된다.

해결책: ReflectionTestUtils로 내부 `restClient` 필드를 교체한다.

```java
@BeforeEach
void setUp() {
    RestClient.Builder mockBuilder = RestClient.builder().baseUrl(BASE_URL);
    mockServer = MockRestServiceServer.bindTo(mockBuilder).build();
    RestClient mockRestClient = mockBuilder.build();

    hibpClient = new HibpClient(RestClient.builder()); // 임시 builder로 생성
    ReflectionTestUtils.setField(hibpClient, "restClient", mockRestClient);
}
```

**Why:** `MockRestServiceServer.RestClientMockRestServiceServerBuilder.injectRequestFactory()`는 빌더에 requestFactory를 설정하지만, HibpClient 생성자가 나중에 다시 `.requestFactory(...)` 를 호출하므로 mock이 무시된다.

**How to apply:** RestClient를 생성자 안에서 완성(.build())하는 모든 Spring 컴포넌트를 테스트할 때 이 패턴을 사용한다. RestClient를 필드로 주입받는 구조라면 일반적인 MockRestServiceServer.bindTo(builder) 패턴이 그대로 동작한다.
