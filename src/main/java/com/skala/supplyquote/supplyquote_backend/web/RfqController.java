package com.skala.supplyquote.supplyquote_backend.web;

import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.domain.RfqStatus;
import com.skala.supplyquote.supplyquote_backend.domain.TaskStatus;
import com.skala.supplyquote.supplyquote_backend.web.dto.AnalysisDto;
import com.skala.supplyquote.supplyquote_backend.web.dto.RfqDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "RFQ", description = "RFQ 등록과 분석 결과 조회 (화면용)")
@RestController
@RequestMapping("/api/rfqs")
public class RfqController {

    @Operation(summary = "RFQ 등록",
            description = "[STUB] 요청서 원문을 저장하고 분석 작업을 접수한다. 분석은 RabbitMQ를 거쳐 비동기로 시작되므로 202와 taskId를 바로 돌려준다. "
                    + "진행 상황은 `GET /api/ai/tasks/{taskId}/stream`(SSE)으로 받는다. 수량·납기·사양은 Agent가 원문에서 추출한다.")
    @ApiResponse(responseCode = "202", description = "접수됨")
    @ApiResponse(responseCode = "400", description = "요청 값 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RfqDto.AcceptedResponse create(@Valid @RequestBody RfqDto.CreateRequest request) {
        return new RfqDto.AcceptedResponse(101L, StubData.TASK_ID, TaskStatus.QUEUED);
    }

    @Operation(summary = "RFQ 목록", description = "[STUB] mine=true면 내 RFQ, false면 전체(담당자 전용). 고객 기준/담당자 기준 화면에 쓴다.")
    @GetMapping
    public List<RfqDto.Summary> list(
            @Parameter(description = "true면 내가 등록한 RFQ만") @RequestParam(defaultValue = "true") boolean mine) {
        return List.of(new RfqDto.Summary(101L, StubData.RFQ_NUMBER, StubData.RFQ_TITLE,
                RfqStatus.WAITING_APPROVAL, StubData.CREATED_AT));
    }

    @Operation(summary = "RFQ 상세 조회", description = "[STUB]")
    @ApiResponse(responseCode = "404", description = "없는 RFQ",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{rfqId}")
    public RfqDto.Detail get(@PathVariable Long rfqId) {
        return new RfqDto.Detail(rfqId, StubData.RFQ_NUMBER, StubData.RFQ_TITLE,
                "# 견적요청서 (RFQ)\n| RFQ 번호 | HB-EV7-EWP-001 |\n| 발행사 | 한별모터스 구매본부 |\n...",
                RfqStatus.WAITING_APPROVAL, StubData.TASK_ID, StubData.CREATED_AT);
    }

    @Operation(summary = "분석 결과 조회",
            description = "[STUB] RFQ의 최신 분석 결과(제품 대조, 이슈·가정·고객 질의서·계약 리스크, 부족 분석, 대안 비교, 제안서)를 돌려준다. "
                    + "분석이 끝나기 전에는 404. 담당자 승인 화면의 데이터원이다.")
    @ApiResponse(responseCode = "404", description = "RFQ가 없거나 아직 분석 결과가 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{rfqId}/analysis")
    public AnalysisDto.Response analysis(@PathVariable Long rfqId) {
        return StubData.analysis(rfqId);
    }
}
