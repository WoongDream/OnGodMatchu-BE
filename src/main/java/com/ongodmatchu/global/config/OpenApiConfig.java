package com.ongodmatchu.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI ({@code /swagger-ui/index.html}) 에서 Bearer 토큰 입력 후 호출 가능하도록 인증 스킴을 정의한다.
 *
 * <p>각 엔드포인트의 사용법 / 응답 스키마 / 에러 코드는 컨트롤러·DTO 의 {@code @Operation} / {@code @Schema} /
 * {@code @ApiResponse} 와 {@code docs/api-development.md} / {@code docs/error-codes.md} 를 참고한다.
 */
@Configuration
public class OpenApiConfig {

  private static final String BEARER_SCHEME_NAME = "bearerAuth";

  @Bean
  public OpenAPI ongodmatchuOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("OnGodMatchu API")
                .description(
                    "한국 퀴즈 앱 OnGodMatchu 의 REST API. 응답은 `ApiResponse<T>` 래핑, 시간은 `OffsetDateTime` (+09:00).\n\n"
                        + "**참고**: `docs/api-development.md` (워크플로우/패턴), `docs/error-codes.md` (에러 카탈로그).")
                .version("v1"))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME_NAME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT accessToken — `Authorization: Bearer <token>`")));
  }
}
