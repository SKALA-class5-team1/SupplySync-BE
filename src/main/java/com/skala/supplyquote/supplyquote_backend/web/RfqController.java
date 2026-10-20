package com.skala.supplyquote.supplyquote_backend.web;

import com.skala.supplyquote.supplyquote_backend.common.ApiException;
import com.skala.supplyquote.supplyquote_backend.common.ErrorCode;
import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.domain.QuoteStatus;
import com.skala.supplyquote.supplyquote_backend.domain.RfqStatus;
import com.skala.supplyquote.supplyquote_backend.domain.TaskStatus;
import com.skala.supplyquote.supplyquote_backend.stub.StubData;
import com.skala.supplyquote.supplyquote_backend.web.dto.AnalysisDto;
import com.skala.supplyquote.supplyquote_backend.web.dto.QuoteViews;
import com.skala.supplyquote.supplyquote_backend.web.dto.RfqDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "RFQ", description = "RFQ 접수와 조회, 분석 결과·견적 조회 (화면용)")
@RestController
@RequestMapping("/api/rfqs")
public class RfqController {

    private static final UUID SAMPLE_TASK_ID = UUID.fromString("3f2b8c1e-5a4d-4e7b-9c1a-2d6f8e0b7a11");

    private final StubData stub;

    public RfqController(StubData stub) {
        this.stub = stub;
    }

    @Operation(summary = "RFQ 접수 (파일 업로드)",
            description = "[STUB] **CUSTOMER 전용.** 요청서 원문 파일(마크다운)과 RFQ 번호를 받아 `rfqs`(RECEIVED)와 분석 작업(`ai_tasks`, QUEUED)을 "
                    + "저장하고 분석을 큐에 넣는다. 분석은 비동기라 202와 taskId를 바로 돌려준다. 진행 상황은 `GET /api/ai/tasks/{taskId}/stream`(SSE)으로 받는다. "
                    + "수량·납기·사양은 Agent가 원문에서 추출해 채우므로 따로 받지 않는다. 같은 rfq_number가 이미 있으면 409.")
    @ApiResponse(responseCode = "202", description = "접수됨")
    @ApiResponse(responseCode = "400", description = "파일이 비었거나 rfq_number 누락",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "이미 접수된 RFQ 번호",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RfqDto.UploadAccepted upload(
            @Parameter(description = "RFQ 원문 파일 (.md)") @RequestPart("file") MultipartFile file,
            @Parameter(description = "요청서에 적힌 RFQ 번호", example = "HB-EV7-EWP-001") @RequestPart("rfq_number") String rfqNumber) {
        if (file.isEmpty() || rfqNumber.isBlank() || rfqNumber.length() > 30) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "파일과 RFQ 번호(30자 이내)가 필요합니다.");
        }
        return new RfqDto.UploadAccepted(1L, rfqNumber, SAMPLE_TASK_ID, TaskStatus.QUEUED);
    }

    @Operation(summary = "RFQ 목록",
            description = "[STUB] **CUSTOMER는 자기 회사 RFQ만, MANAGER는 전체**를 본다. mine=true면 내가 올린 RFQ만.")
    @GetMapping
    public List<RfqDto.Summary> list(
            @Parameter(description = "true면 내가 올린 RFQ만") @RequestParam(name = "mine", defaultValue = "false") boolean mine) {
        return List.of(new RfqDto.Summary(1L, StubData.RFQ_NUMBER, "한별모터스", "전동식 워터펌프 (EWP, 12V)",
                RfqStatus.IN_REVIEW, LocalDateTime.parse("2026-10-12T09:00:00")));
    }

    @Operation(summary = "RFQ 상세",
            description = "[STUB] 추출 컬럼(eau, sample_qty 등)은 SG1이 요청서를 읽은 뒤에 채워진다. 다른 회사의 RFQ는 404.")
    @ApiResponse(responseCode = "404", description = "없는 RFQ (다른 회사 RFQ 포함)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{rfqId}")
    public RfqDto.Detail get(@PathVariable Long rfqId) {
        return new RfqDto.Detail(rfqId, StubData.RFQ_NUMBER, 1L, "한별모터스", "전동식 워터펌프 (EWP, 12V)",
                RfqStatus.IN_REVIEW, LocalDateTime.parse("2026-10-12T09:00:00"),
                120000, 2000, LocalDate.parse("2026-12-15"), LocalDate.parse("2027-09-01"), 38000,
                LocalDate.parse("2026-10-30"), SAMPLE_TASK_ID);
    }

    @Operation(summary = "분석 결과 + 견적 (승인 화면)",
            description = "[STUB] **MANAGER 전용.** 분석 결과(rfq_reviews: 이슈, 가정, 고객 질의서, 계약 리스크, 부족 분석, 대안 4종)와 "
                    + "견적(원가·마진·검토 결과 포함)을 돌려준다. 이 데이터는 내부용이라 고객에게는 403이다.")
    @ApiResponse(responseCode = "403", description = "MANAGER가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "RFQ가 없거나 아직 분석 결과가 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{rfqId}/analysis")
    public AnalysisDto.AnalysisView analysis(@PathVariable Long rfqId) {
        return new AnalysisDto.AnalysisView(stub.review(), List.of(stub.managerQuote(QuoteStatus.DRAFT)));
    }

    @Operation(summary = "견적 목록 (고객용)",
            description = "[STUB] **CUSTOMER는 REGISTERED 견적만**, 고객 공개 필드만 받는다 (원가·마진·검토 결과 없음, 연도별 마진 제외). "
                    + "등록 전에는 빈 목록이다.")
    @GetMapping("/{rfqId}/quotes")
    public List<QuoteViews.CustomerQuote> quotes(@PathVariable Long rfqId) {
        return List.of(stub.customerQuote());
    }
}
