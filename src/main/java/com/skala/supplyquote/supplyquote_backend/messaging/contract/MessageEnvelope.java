package com.skala.supplyquote.supplyquote_backend.messaging.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * 모든 메시지의 공통 봉투. body는 type별 payload이다.
 *
 * @param messageId     메시지 한 건의 고유 ID. 소비자가 중복 수신을 걸러내는 키(processed_messages)
 * @param requestId     업무 요청의 멱등 키. 재시도·재발행해도 같은 값
 * @param type          routing key와 같은 값 (예: analysis.requested)
 * @param schemaVersion {@link MessagingContract#SCHEMA_VERSION}
 * @param occurredAt    발생 시각 (UTC)
 */
public record MessageEnvelope<T>(
        UUID messageId,
        UUID requestId,
        String type,
        int schemaVersion,
        Instant occurredAt,
        T payload) {

    public static <T> MessageEnvelope<T> of(UUID requestId, String type, T payload) {
        return new MessageEnvelope<>(UUID.randomUUID(), requestId, type,
                MessagingContract.SCHEMA_VERSION, Instant.now(), payload);
    }
}
