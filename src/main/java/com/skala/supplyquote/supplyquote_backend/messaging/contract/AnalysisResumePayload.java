package com.skala.supplyquote.supplyquote_backend.messaging.contract;

import java.util.UUID;

/**
 * analysis.resume: 사람의 판단 뒤 멈춰 있던 Agent를 같은 thread로 재개한다.
 * 생산자는 요구 확인 API·승인 API 처리 직후의 Spring이고, 소비자(Spring)가 Agent 서버의 resume API를 호출한다.
 * 판단 내용(가정 값, 승인 의견)은 메시지에 싣지 않고 소비자가 DB에서 읽는다.
 *
 * @param taskId   ai_tasks.id
 * @param rfqId    rfqs.id
 * @param threadId LangGraph thread id
 * @param kind     무엇에 대한 재개인지
 */
public record AnalysisResumePayload(
        UUID taskId,
        Long rfqId,
        String threadId,
        Kind kind) {

    public enum Kind {
        /** 요구 확인(가정 승인) 후 재개 */
        CLARIFICATION,
        /** 승인 후 재개: 최신 조건 재검증과 등록으로 진행 */
        APPROVED,
        /** 반려 후 재개: 해당 단계로 돌아가 수정 */
        REJECTED
    }
}
