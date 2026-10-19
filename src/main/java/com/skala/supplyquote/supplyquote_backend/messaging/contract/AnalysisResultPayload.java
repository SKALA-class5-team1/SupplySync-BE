package com.skala.supplyquote.supplyquote_backend.messaging.contract;

import com.skala.supplyquote.supplyquote_backend.domain.TaskStatus;
import java.util.UUID;

/**
 * analysis.completed / analysis.failed: 분석 결과 저장(또는 실패 확정) 직후 Spring이 발행하고,
 * Spring 소비자가 받아 화면 알림(SSE)을 보낸다.
 *
 * @param taskId          ai_tasks.id
 * @param rfqId           rfqs.id
 * @param taskStatus      작업의 새 상태 (completed면 WAITING_APPROVAL 등, failed면 FAILED)
 * @param analysisResultId completed일 때만 값이 있다
 * @param resultVersion   completed일 때만 값이 있다
 * @param failureReason   failed일 때만 값이 있다
 */
public record AnalysisResultPayload(
        UUID taskId,
        Long rfqId,
        TaskStatus taskStatus,
        Long analysisResultId,
        Integer resultVersion,
        String failureReason) {
}
