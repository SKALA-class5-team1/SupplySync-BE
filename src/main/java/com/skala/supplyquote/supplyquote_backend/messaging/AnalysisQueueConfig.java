package com.skala.supplyquote.supplyquote_backend.messaging;

import com.skala.supplyquote.supplyquote_backend.messaging.contract.MessagingContract;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@Configuration
@EnableRabbit
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
public class AnalysisQueueConfig {
    @Bean
    Declarables analysisQueueTopology() {
        var exchange = new TopicExchange(MessagingContract.EXCHANGE, true, false);
        var dlx = new DirectExchange(MessagingContract.DEAD_LETTER_EXCHANGE, true, false);
        Queue requests = QueueBuilder.durable(MessagingContract.QUEUE_ANALYSIS_REQUEST)
                .deadLetterExchange(MessagingContract.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(MessagingContract.QUEUE_ANALYSIS_REQUEST_DLQ).build();
        Queue dead = QueueBuilder.durable(MessagingContract.QUEUE_ANALYSIS_REQUEST_DLQ).build();
        return new Declarables(exchange, dlx, requests, dead,
                BindingBuilder.bind(requests).to(exchange).with(MessagingContract.RK_ANALYSIS_REQUESTED),
                BindingBuilder.bind(requests).to(exchange).with(MessagingContract.RK_ANALYSIS_RESUME),
                BindingBuilder.bind(dead).to(dlx).with(MessagingContract.QUEUE_ANALYSIS_REQUEST_DLQ));
    }
}
