package com.skala.supplyquote.supplyquote_backend.messaging.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import java.util.Map;

/**
 * analysis.resume: 사람의 판단 뒤 멈춰 있던 Agent를 같은 thread로 재개한다.
 * 생산자는 요구 확인 API·승인 API 처리 직후의 Spring이고, 소비자(Spring)가 Agent 서버의 resume API를 호출한다.
 * 재개 결정은 API 처리 시점의 값을 보존한다. RFQ 원문은 싣지 않는다.
 *
 * @param taskId   ai_tasks.id
 * @param rfqId    rfqs.id
 * @param threadId LangGraph thread id
 * @param kind     무엇에 대한 재개인지
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisResumePayload(
        UUID taskId,
        Long rfqId,
        String threadId,
        Kind kind,
        Long userId,
        Map<String, Object> decision) {

    public enum Kind {
        /** 요구 확인(가정 승인) 후 재개 */
        CLARIFICATION,
        /** 승인 후 재개: 최신 조건 재검증과 등록으로 진행 */
        APPROVED,
        /** 종료 반려 */
        REJECTED,
        /** 견적 수정 후 SG3 재작성 */
        REVISE
    }
}
