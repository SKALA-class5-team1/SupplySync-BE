package com.skala.supplyquote.supplyquote_backend.internal;

import com.skala.supplyquote.supplyquote_backend.domain.TaskStatus;
import com.skala.supplyquote.supplyquote_backend.internal.dto.EventDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부 - 진행 이벤트", description = "Agent용: 진행 이벤트 전달. docker network 안에서만 열린다.")
@RestController
@RequestMapping("/internal/tasks")
public class InternalEventController {

    @Operation(summary = "진행 이벤트 전달",
            description = "[STUB] Agent가 단계 시작·종료, 도구 실행, 사람 대기를 알린다. Spring은 작업 상태(ai_tasks.status, current_step)를 갱신하고 열려 있는 SSE 연결로 중계한다. "
                    + "CLARIFICATION_REQUIRED → NEEDS_CLARIFICATION, APPROVAL_REQUIRED → WAITING_APPROVAL, DONE → DONE, FAILED → FAILED로 전이한다. "
                    + "TOKEN처럼 빈도가 높은 이벤트는 큐(RabbitMQ)를 거치지 않고 이 HTTP로 직접 보낸다. 고객 구독자에게는 이슈 내용이 담긴 이벤트를 중계하지 않는다.")
    @PostMapping("/{taskId}/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventDto.EventAccepted event(@PathVariable UUID taskId, @Valid @RequestBody EventDto.EventRequest request) {
        TaskStatus status = switch (request.type()) {
            case CLARIFICATION_REQUIRED -> TaskStatus.NEEDS_CLARIFICATION;
            case APPROVAL_REQUIRED -> TaskStatus.WAITING_APPROVAL;
            case DONE -> TaskStatus.DONE;
            case FAILED -> TaskStatus.FAILED;
            default -> TaskStatus.RUNNING;
        };
        return new EventDto.EventAccepted(taskId, status);
    }
}
