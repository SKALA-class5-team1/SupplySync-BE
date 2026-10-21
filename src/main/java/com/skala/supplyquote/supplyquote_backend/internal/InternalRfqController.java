package com.skala.supplyquote.supplyquote_backend.internal;

import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.internal.dto.InternalRfqDto;
import com.skala.supplyquote.supplyquote_backend.service.RfqQueryService;
import com.skala.supplyquote.supplyquote_backend.service.RfqCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부 - RFQ", description = "business MCP·Agent용: RFQ 원문 조회와 추출 값 저장 (SG1). X-Internal-Api-Key 헤더가 필요하다.")
@RestController
@RequestMapping("/internal/rfqs")
public class InternalRfqController {

    private final RfqQueryService rfqs;
    private final RfqCommandService commands;

    public InternalRfqController(RfqQueryService rfqs, RfqCommandService commands) {
        this.rfqs = rfqs;
        this.commands = commands;
    }

    @Operation(summary = "RFQ 정보 조회", description = "DB에 저장된 RFQ의 메타 정보와 추출 컬럼.")
    @ApiResponse(responseCode = "404", description = "없는 RFQ",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{rfqNumber}")
    public InternalRfqDto.RfqInfo get(@PathVariable String rfqNumber) {
        return rfqs.internal(rfqNumber);
    }

    @Operation(summary = "RFQ 원문 조회",
            description = "DB에 기록된 파일 경로에서 요청서 원문(마크다운)을 읽어 반환한다.",
            responses = @ApiResponse(responseCode = "200", description = "RFQ 원문",
                    content = @Content(mediaType = "text/markdown", schema = @Schema(type = "string"))))
    @GetMapping(value = "/{rfqNumber}/content", produces = "text/markdown;charset=UTF-8")
    public String content(@PathVariable String rfqNumber) {
        return rfqs.content(rfqNumber);
    }

    @Operation(summary = "RFQ 추출 값 저장",
            description = "SG1이 요청서를 읽은 뒤 연간 물량, 시작품 수량, 납기, 양산 시작, 목표 단가, 견적 마감일을 채운다. "
                    + "전달한 필드만 갱신한다. status는 이 API로 RECEIVED → IN_REVIEW만 바꿀 수 있다. QUOTED는 견적 등록 시 서버가 자동으로 바꾼다.")
    @PatchMapping("/{rfqNumber}")
    public InternalRfqDto.RfqInfo patch(@PathVariable String rfqNumber, @RequestBody InternalRfqDto.RfqPatch patch) {
        return commands.patch(rfqNumber, patch);
    }
}
