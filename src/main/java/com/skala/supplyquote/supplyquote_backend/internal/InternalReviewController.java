package com.skala.supplyquote.supplyquote_backend.internal;

import com.skala.supplyquote.supplyquote_backend.internal.dto.ReviewUpsertDto;
import com.skala.supplyquote.supplyquote_backend.service.ReviewQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부 - 분석 결과", description = "Agent용: 분석 결과(rfq_reviews) 저장 (SG1·SG2). X-Internal-Api-Key 헤더가 필요하다.")
@RestController
@RequestMapping("/internal/rfq-reviews")
public class InternalReviewController {
    private final ReviewQuoteService service;

    public InternalReviewController(ReviewQuoteService service) { this.service = service; }

    @Operation(summary = "분석 결과 upsert",
            description = "(rfq_number, thread_id)로 upsert하고 **전달한 섹션만 갱신**한다. SG1이 issues, selected_model 등을 먼저 저장하고, "
                    + "SG2가 이어서 shortages, alternatives, recommended_alt를 저장한다. 같은 요청을 다시 보내도 결과가 같다(멱등). "
                    + "status는 RUNNING, WAITING(담당자 입력 대기), DONE. 저장 시 작업 상태·SSE 이벤트는 별도로 `/internal/tasks/{id}/events`로 알린다.")
    @PutMapping
    public ReviewUpsertDto.ReviewUpsertResponse upsert(@Valid @RequestBody ReviewUpsertDto.ReviewUpsertRequest request) {
        return service.upsert(request);
    }
}
