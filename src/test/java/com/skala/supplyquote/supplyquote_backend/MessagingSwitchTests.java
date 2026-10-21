package com.skala.supplyquote.supplyquote_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.skala.supplyquote.supplyquote_backend.messaging.AnalysisRequestConsumer;
import com.skala.supplyquote.supplyquote_backend.service.AgentGateway;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class MessagingSwitchTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AnalysisRequestConsumer.class)
            .withBean(JdbcTemplate.class, () -> Mockito.mock(JdbcTemplate.class))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withBean(AgentGateway.class, () -> Mockito.mock(AgentGateway.class));

    @Test
    void consumerRunsOnlyWhenMessagingAndConsumerAreEnabled() {
        runner.withPropertyValues("app.messaging.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(AnalysisRequestConsumer.class));
        runner.withPropertyValues("app.messaging.enabled=true", "app.messaging.consumer-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(AnalysisRequestConsumer.class));
    }

    @Test
    void consumerIsOffWhenAgentConsumesTheQueueOrMessagingIsDisabled() {
        runner.withPropertyValues("app.messaging.enabled=true", "app.messaging.consumer-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AnalysisRequestConsumer.class));
        runner.withPropertyValues("app.messaging.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AnalysisRequestConsumer.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(AnalysisRequestConsumer.class));
    }
}
