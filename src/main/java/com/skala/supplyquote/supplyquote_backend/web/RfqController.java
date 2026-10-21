package com.skala.supplyquote.supplyquote_backend.web;

import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.auth.CurrentUser;
import com.skala.supplyquote.supplyquote_backend.service.RfqQueryService;
import com.skala.supplyquote.supplyquote_backend.service.RfqCommandService;
import com.skala.supplyquote.supplyquote_backend.service.ReviewQuoteService;
import com.skala.supplyquote.supplyquote_backend.web.dto.AnalysisDto;
import com.skala.supplyquote.supplyquote_backend.web.dto.QuoteViews;
import com.skala.supplyquote.supplyquote_backend.web.dto.RfqDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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

    private final RfqQueryService rfqs;
    private final RfqCommandService commands;
    private final ReviewQuoteService reviews;
    private final CurrentUser current;

    public RfqController(RfqQueryService rfqs, RfqCommandService commands, ReviewQuoteService reviews, CurrentUser current) {
        this.rfqs = rfqs;
        this.commands = commands;
        this.reviews = reviews;
        this.current = current;
    }

    @Operation(summary = "RFQ 접수 (파일 업로드)",
            description = "마크다운 파일과 RFQ 번호를 저장하고 분석 작업을 생성한다. AGENT_BASE_URL 설정 시 커밋 뒤 Agent 실행 HTTP를 호출한다. "
                    + "설정하지 않으면 작업은 QUEUED로 남는다. 로그인한 고객 계정으로 접수한다. 같은 RFQ 번호는 409.")
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
        return commands.upload(file, rfqNumber);
    }

    @Operation(summary = "RFQ 목록",
            description = "DB에 저장된 RFQ를 최신순으로 조회한다. 고객은 소속 회사 RFQ만, mine=true는 본인이 올린 RFQ만 조회한다.")
    @GetMapping
    public List<RfqDto.Summary> list(
            @Parameter(description = "true면 내가 올린 RFQ만") @RequestParam(name = "mine", defaultValue = "false") boolean mine) {
        return rfqs.list(current.get(), mine);
    }

    @Operation(summary = "RFQ 상세",
            description = "DB에 저장된 RFQ 상세. 추출 컬럼은 SG1이 채우기 전에는 null이다. 고객은 소속 회사 RFQ만 볼 수 있다.")
    @ApiResponse(responseCode = "404", description = "없는 RFQ (다른 회사 RFQ 포함)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{rfqId}")
    public RfqDto.Detail get(@PathVariable Long rfqId) {
        current.requireRfq(rfqId);
        return rfqs.detail(rfqId);
    }

    @Operation(summary = "분석 결과 + 견적 (승인 화면)",
            description = "담당자에게 DB의 최근 분석 결과와 원가·마진을 포함한 견적 이력을 반환한다.")
    @ApiResponse(responseCode = "403", description = "MANAGER가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "RFQ가 없거나 아직 분석 결과가 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{rfqId}/analysis")
    public AnalysisDto.AnalysisView analysis(@PathVariable Long rfqId) {
        current.requireManagerId();
        return reviews.analysis(rfqId);
    }

    @Operation(summary = "견적 목록 (고객용)",
            description = "REGISTERED 견적만 고객 공개 필드로 반환한다 (원가·마진·검토 결과 없음, 연도별 마진 제외). "
                    + "등록 전에는 빈 목록이다.")
    @GetMapping("/{rfqId}/quotes")
    public List<QuoteViews.CustomerQuote> quotes(@PathVariable Long rfqId) {
        current.requireRfq(rfqId);
        return reviews.customerQuotes(rfqId);
    }
}
