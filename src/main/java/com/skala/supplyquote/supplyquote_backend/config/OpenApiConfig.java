package com.skala.supplyquote.supplyquote_backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: /swagger-ui.html
 * 화면 상단 드롭다운에서 화면용 API(/api)와 내부 업무 API(/internal)를 나눠 본다.
 * 메시지 규약(RabbitMQ)은 REST가 아니므로 docs/MESSAGING.md에 둔다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";
    private static final String INTERNAL_KEY = "internalApiKey";

    @Bean
    OpenAPI supplySyncOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SupplySync Backend API")
                        .version("v0.1")
                        .description("""
                                공급망 연계 RFQ·재고·구매 대응 Agent 백엔드.

                                - **화면용 API** (`/api/**`): Frontend가 호출한다. JWT 인증.
                                - **내부 업무 API** (`/internal/**`): business MCP와 Agent가 호출한다. `X-Internal-Api-Key` 헤더.

                                `[STUB]`로 시작하는 오퍼레이션은 계약 확정용 고정 응답이다. 구현되면 표시가 사라진다."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes(INTERNAL_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Api-Key")));
    }

    @Bean
    GroupedOpenApi webApi() {
        return GroupedOpenApi.builder()
                .group("1-web")
                .displayName("1. 화면용 API (/api)")
                .pathsToMatch("/api/**")
                .addOpenApiCustomizer(api -> api.addSecurityItem(new SecurityRequirement().addList(BEARER)))
                .build();
    }

    @Bean
    GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("2-internal")
                .displayName("2. 내부 업무 API (/internal)")
                .pathsToMatch("/internal/**")
                .addOpenApiCustomizer(api -> api.addSecurityItem(new SecurityRequirement().addList(INTERNAL_KEY)))
                .build();
    }
}
