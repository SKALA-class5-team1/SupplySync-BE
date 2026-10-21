package com.skala.supplyquote.supplyquote_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: /swagger-ui.html
 * 화면 상단 드롭다운에서 화면용 API(/api)와 내부 업무 API(/internal)를 나눠 본다.
 * 메시지 규약(RabbitMQ)은 REST가 아니므로 docs/BACKEND.md에 둔다.
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

                                JSON 필드명은 **snake_case**이다. 데이터셋 정답지(05_expected)와 같은 이름을 쓴다.
                                모든 쓰기는 이 API(Spring)를 거친다. MCP·Agent는 DB에 직접 쓰지 않는다.

                                화면용 인증과 내부 API 키는 서로 별개다."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes(INTERNAL_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Api-Key")));
    }

    /**
     * 런타임 JSON은 snake_case(spring.jackson.property-naming-strategy)인데, springdoc은 스키마를 Jackson 2로 만들기 때문에
     * 이 설정이 반영되지 않아 문서에는 camelCase가 나온다. 문서가 계약이므로 스키마 생성에도 같은 규칙을 적용한다.
     * 문서 JSON 자체를 만드는 매퍼(objectMapperProvider)의 키가 바뀌지 않도록 **복사본**만 바꾼다.
     */
    @Bean
    ModelResolver snakeCaseModelResolver(ObjectMapperProvider objectMapperProvider) {
        ObjectMapper mapper = objectMapperProvider.jsonMapper().copy();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return new ModelResolver(mapper);
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
