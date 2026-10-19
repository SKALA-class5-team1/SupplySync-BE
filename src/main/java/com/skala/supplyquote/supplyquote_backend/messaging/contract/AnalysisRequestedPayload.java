package com.skala.supplyquote.supplyquote_backend.messaging.contract;

import java.util.UUID;

/**
 * analysis.requested: RFQ 등록 직후 Spring(생산자)이 발행하고, Spring(소비자)이 받아 Agent 서버를 호출한다.
 *
 * @param taskId        ai_tasks.id
 * @param rfqId         rfqs.id
 * @param requestedBy   요청한 users.id
 * @param threadId      LangGraph thread id. 요구 확인·승인 후 같은 thread로 재개한다
 */
public record AnalysisRequestedPayload(
        UUID taskId,
        Long rfqId,
        Long requestedBy,
        String threadId) {
}
