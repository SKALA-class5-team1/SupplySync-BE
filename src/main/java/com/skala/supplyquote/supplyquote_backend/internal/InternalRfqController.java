package com.skala.supplyquote.supplyquote_backend.internal;

import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.domain.RfqStatus;
import com.skala.supplyquote.supplyquote_backend.internal.dto.InternalRfqDto;
import com.skala.supplyquote.supplyquote_backend.stub.StubData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부 - RFQ", description = "business MCP·Agent용: RFQ 원문 조회와 추출 값 저장 (SG1). docker network 안에서만 열린다.")
@RestController
@RequestMapping("/internal/rfqs")
public class InternalRfqController {

    private final StubData stub;

    public InternalRfqController(StubData stub) {
        this.stub = stub;
    }

    @Operation(summary = "RFQ 정보 조회", description = "[STUB] 접수된 RFQ의 메타 정보와 추출 컬럼.")
    @ApiResponse(responseCode = "404", description = "없는 RFQ",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{rfqNumber}")
    public InternalRfqDto.RfqInfo get(@PathVariable String rfqNumber) {
        return new InternalRfqDto.RfqInfo(1L, rfqNumber, 1L, "전동식 워터펌프 (EWP, 12V)", RfqStatus.RECEIVED,
                "01_rfq/RFQ_HB-EV7-EWP-001.md", null, null, null, null, null, null);
    }

    @Operation(summary = "RFQ 원문 조회",
            description = "[STUB] 접수된 요청서 원문(마크다운)을 돌려준다. Agent는 파일 경로를 직접 열지 않고 이 API로 원문을 받는다 (parse_rfq 입력).",
            responses = @ApiResponse(responseCode = "200", description = "RFQ 원문",
                    content = @Content(mediaType = "text/markdown", schema = @Schema(type = "string"))))
    @GetMapping(value = "/{rfqNumber}/content", produces = "text/markdown;charset=UTF-8")
    public String content(@PathVariable String rfqNumber) {
        return stub.rfqContent();
    }

    @Operation(summary = "RFQ 추출 값 저장",
            description = "[STUB] SG1이 요청서를 읽은 뒤 연간 물량, 시작품 수량, 납기, 양산 시작, 목표 단가, 견적 마감일을 채운다. "
                    + "전달한 필드만 갱신한다. status는 RECEIVED → IN_REVIEW → QUOTED 순서로만 바꿀 수 있다 (QUOTED는 견적 등록 시 자동).")
    @PatchMapping("/{rfqNumber}")
    public InternalRfqDto.RfqInfo patch(@PathVariable String rfqNumber, @RequestBody InternalRfqDto.RfqPatch patch) {
        return new InternalRfqDto.RfqInfo(1L, rfqNumber, 1L, "전동식 워터펌프 (EWP, 12V)",
                patch.status() != null ? patch.status() : RfqStatus.IN_REVIEW, "01_rfq/RFQ_HB-EV7-EWP-001.md",
                patch.eau(), patch.sampleQty(), patch.sampleDueDate(), patch.sopDate(), patch.targetPriceKrw(),
                patch.quoteDueDate() != null ? patch.quoteDueDate() : LocalDate.parse("2026-10-30"));
    }
}
