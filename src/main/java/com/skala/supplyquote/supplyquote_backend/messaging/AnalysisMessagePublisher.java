package com.skala.supplyquote.supplyquote_backend.messaging;

import com.skala.supplyquote.supplyquote_backend.messaging.contract.MessagingContract;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.stereotype.Component;

@Component
public class AnalysisMessagePublisher {
    private final RabbitTemplate rabbit;

    public AnalysisMessagePublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
        rabbit.setMandatory(true);
    }

    public void publish(String body, UUID messageId, UUID requestId, String type, int schemaVersion) {
        var props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setMessageId(messageId.toString());
        props.setCorrelationId(requestId.toString());
        props.setHeader(MessagingContract.HEADER_MESSAGE_ID, messageId.toString());
        props.setHeader(MessagingContract.HEADER_REQUEST_ID, requestId.toString());
        props.setHeader(MessagingContract.HEADER_SCHEMA_VERSION, schemaVersion);
        var confirm = new CorrelationData(messageId.toString());
        rabbit.send(MessagingContract.EXCHANGE, type,
                new Message(body.getBytes(java.nio.charset.StandardCharsets.UTF_8), props), confirm);
        try {
            var result = confirm.getFuture().get(5, TimeUnit.SECONDS);
            if (!result.ack() || confirm.getReturned() != null)
                throw new IllegalStateException("RabbitMQ가 분석 메시지를 접수하지 않았습니다: " + messageId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RabbitMQ 발행 확인이 중단됐습니다.", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("RabbitMQ 발행 확인에 실패했습니다.", e);
        }
    }
}
