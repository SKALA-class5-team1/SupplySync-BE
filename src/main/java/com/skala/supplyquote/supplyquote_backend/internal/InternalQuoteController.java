package com.skala.supplyquote.supplyquote_backend.internal;

import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.internal.dto.QuoteInternalDto;
import com.skala.supplyquote.supplyquote_backend.service.ReviewQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부 - 견적", description = "Agent·business MCP용: 견적 초안 생성(SG3), 검토 결과 갱신, 등록(SG4). 내부 API 인증은 다음 단계에서 적용한다.")
@RestController
@RequestMapping("/internal/quotes")
public class InternalQuoteController {
    private final ReviewQuoteService service;

    public InternalQuoteController(ReviewQuoteService service) { this.service = service; }

    @Operation(summary = "견적 초안 생성",
            description = "SG3가 견적 초안을 저장한다. 상태는 DRAFT이고 **quote_id는 서버가 `Q-YYYY-MMDD-NNN`(발행일 + 그날 일련번호)으로 생성**해서 돌려준다. "
                    + "thread_id로 연결된 분석 결과(rfq_reviews)가 없으면 404. 첫 견적 revision=1, REVISE마다 +1(최대 4)이다. "
                    + "같은 thread_id+revision 재요청은 기존 quote_id를 반환하고, 새 revision은 REVISE 승인 후에만 생성한다. "
                    + "저장 컬럼과 비저장 필드는 QuoteDraftRequest 설명 참고.")
    @ApiResponse(responseCode = "201", description = "초안 생성됨")
    @ApiResponse(responseCode = "404", description = "thread_id에 해당하는 분석 결과 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteInternalDto.QuoteCreated create(@Valid @RequestBody QuoteInternalDto.QuoteDraftRequest request) {
        return service.create(request);
    }

    @Operation(summary = "견적 검토 결과·조건 갱신",
            description = "DRAFT 상태일 때만 checks, conditions를 갱신한다. 전달한 필드만 바뀐다. DRAFT가 아니면 409.")
    @ApiResponse(responseCode = "409", description = "DRAFT가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{quoteId}")
    public QuoteInternalDto.QuoteCreated patch(@PathVariable String quoteId, @RequestBody QuoteInternalDto.QuotePatchRequest request) {
        return service.patch(quoteId, request);
    }

    @Operation(summary = "견적 등록 (멱등)",
            description = "SG4가 재검증을 마친 뒤 호출한다. **APPROVED인 견적만** REGISTERED로 바꾸고 RFQ를 QUOTED로, 작업을 REGISTERING으로 바꾼다. Agent의 RUN_END가 DONE을 확정한다. "
                    + "`UPDATE quotes SET status='REGISTERED' WHERE quote_id=? AND status='APPROVED'`로 처리해서 중복 호출해도 안전하다. "
                    + "신규 등록이면 201 CREATED, 이미 REGISTERED이면 200 ALREADY_PROCESSED, 승인되지 않은 견적이면 409.")
    @ApiResponse(responseCode = "201", description = "새로 등록됨 (outcome=CREATED)")
    @ApiResponse(responseCode = "200", description = "이미 등록됨 (outcome=ALREADY_PROCESSED)")
    @ApiResponse(responseCode = "409", description = "CONFLICT_STATE: 승인되지 않은 견적 (DRAFT 또는 REJECTED)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{quoteId}/register")
    public ResponseEntity<QuoteInternalDto.RegisterResponse> register(@PathVariable String quoteId) {
        var body = service.register(quoteId);
        return ResponseEntity.status("CREATED".equals(body.outcome()) ? HttpStatus.CREATED : HttpStatus.OK).body(body);
    }
}
