package com.skala.supplyquote.supplyquote_backend.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.skala.supplyquote.supplyquote_backend.domain.AgentStep;
import com.skala.supplyquote.supplyquote_backend.domain.EventType;
import com.skala.supplyquote.supplyquote_backend.domain.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public final class EventDto {

    private EventDto() {
    }

    @Schema(description = "Agent가 보내는 진행 이벤트. Spring이 상태를 갱신하고 SSE로 화면에 중계한다.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventRequest(
            @NotNull @Schema(example = "STEP_START") EventType type,
            @Schema(example = "SUPPLY_CHECK") AgentStep step,
            @Schema(description = "화면에 보여줄 짧은 메시지", example = "재고 조회 중") String message,
            @Size(max = 2048) @Schema(description = "Agent 실행 추적 링크. 처음 알게 된 이벤트 또는 이후 이벤트에 넣으면 작업에 저장된다") String traceUrl,
            @JsonAlias("payload") @Schema(description = "RUN_END는 outcome=REGISTERED|REJECTED. payload 별칭도 허용") Map<String, Object> data) {
    }

    @Schema(description = "이벤트 접수 결과")
    public record EventAccepted(
            UUID taskId,
            @Schema(description = "이벤트 반영 후 작업 상태", example = "RUNNING") TaskStatus status) {
    }
}
