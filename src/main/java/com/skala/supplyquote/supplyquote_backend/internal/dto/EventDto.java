package com.skala.supplyquote.supplyquote_backend.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.skala.supplyquote.supplyquote_backend.domain.AgentStep;
import com.skala.supplyquote.supplyquote_backend.domain.EventType;
import com.skala.supplyquote.supplyquote_backend.domain.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
            @Schema(description = "이벤트별 부가 정보. CLARIFICATION_REQUIRED는 issues, TOOL_*는 tool 이름 등") Map<String, Object> data) {
    }

    @Schema(description = "이벤트 접수 결과")
    public record EventAccepted(
            UUID taskId,
            @Schema(description = "이벤트 반영 후 작업 상태", example = "RUNNING") TaskStatus status) {
    }
}
