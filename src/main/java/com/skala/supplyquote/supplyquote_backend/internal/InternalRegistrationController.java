package com.skala.supplyquote.supplyquote_backend.internal;

import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.domain.AnalysisResultStatus;
import com.skala.supplyquote.supplyquote_backend.internal.dto.RegistrationDto;
import com.skala.supplyquote.supplyquote_backend.web.dto.AnalysisDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부 - 저장·등록", description = "SG1 검토 기록, 분석 결과 저장, 견적·구매요청 등록. Spring이 권한·승인·버전을 검사한 뒤 저장한다.")
@RestController
@RequestMapping("/internal")
public class InternalRegistrationController {

    @Operation(summary = "SG1 검토 결과 기록",
            description = "[STUB] RFQ 검토(요구 추출·검증·제품 대조) 결과를 rfq_reviews에 남긴다. business MCP의 검토 요청 도구가 호출한다. "
                    + "같은 rfqId로 다시 호출하면 이력이 한 행 더 쌓인다(멱등 키 없음).")
    @ApiResponse(responseCode = "201", description = "기록됨")
    @PostMapping("/rfq-reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationDto.RfqReviewResponse recordReview(@Valid @RequestBody RegistrationDto.RfqReviewRequest request) {
        return new RegistrationDto.RfqReviewResponse(1, Instant.now());
    }

    @Operation(summary = "분석 결과 저장",
            description = "[STUB] Agent가 분석을 끝내면 호출한다. 서버가 version을 부여하고 상태를 WAITING_APPROVAL로 둔 뒤 "
                    + "`analysis.completed` 메시지를 발행한다(docs/MESSAGING.md).")
    @ApiResponse(responseCode = "201", description = "저장됨")
    @PostMapping("/analysis-results")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisDto.SaveResponse saveAnalysis(@Valid @RequestBody AnalysisDto.SaveRequest request) {
        return new AnalysisDto.SaveResponse(11L, request.taskId(), 1, AnalysisResultStatus.WAITING_APPROVAL);
    }

    @Operation(summary = "견적 등록",
            description = "[STUB] 승인 상태와 analysisResultVersion을 검사한 뒤 내부 견적을 저장한다. "
                    + "같은 requestId로 이미 성공했으면 저장하지 않고 200 ALREADY_PROCESSED를 돌려준다.")
    @ApiResponse(responseCode = "201", description = "새로 저장됨 (outcome=CREATED)")
    @ApiResponse(responseCode = "200", description = "이미 처리됨 (outcome=ALREADY_PROCESSED)")
    @ApiResponse(responseCode = "403", description = "승인되지 않은 결과",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "STALE_VERSION: 승인받은 버전이 최신이 아님 → 재계산·재승인 필요",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/quotes")
    public ResponseEntity<RegistrationDto.Response> registerQuote(@Valid @RequestBody RegistrationDto.QuoteRequest request) {
        var body = new RegistrationDto.Response(request.requestId(), RegistrationDto.Step.QUOTE,
                RegistrationDto.Outcome.CREATED, 55L, Instant.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "구매요청 등록",
            description = "[STUB] 견적 등록과 같은 requestId를 쓴다. 견적은 성공하고 구매요청만 실패했다면, 같은 requestId로 이 API만 다시 호출한다.")
    @ApiResponse(responseCode = "201", description = "새로 저장됨 (outcome=CREATED)")
    @ApiResponse(responseCode = "200", description = "이미 처리됨 (outcome=ALREADY_PROCESSED)")
    @ApiResponse(responseCode = "409", description = "STALE_VERSION",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/purchase-requests")
    public ResponseEntity<RegistrationDto.Response> registerPurchaseRequest(
            @Valid @RequestBody RegistrationDto.PurchaseRequestRequest request) {
        var body = new RegistrationDto.Response(request.requestId(), RegistrationDto.Step.PURCHASE_REQUEST,
                RegistrationDto.Outcome.CREATED, 77L, Instant.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "등록 단계별 현황 조회", description = "[STUB] 재시도 전에 QUOTE, PURCHASE_REQUEST 중 어느 단계가 실패했는지 확인한다.")
    @GetMapping("/registrations/{requestId}")
    public List<RegistrationDto.StepStatus> registration(@PathVariable UUID requestId) {
        return List.of(
                new RegistrationDto.StepStatus(RegistrationDto.Step.QUOTE, "SUCCEEDED", 55L, null, Instant.now()),
                new RegistrationDto.StepStatus(RegistrationDto.Step.PURCHASE_REQUEST, "FAILED", null,
                        "대체 부품 P-MCU-G1-ALT 가용 재고 변경으로 재검증 필요", Instant.now()));
    }
}
