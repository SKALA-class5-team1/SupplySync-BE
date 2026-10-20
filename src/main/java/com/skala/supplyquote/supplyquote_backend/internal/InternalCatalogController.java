package com.skala.supplyquote.supplyquote_backend.internal;

import com.skala.supplyquote.supplyquote_backend.common.ApiException;
import com.skala.supplyquote.supplyquote_backend.common.ErrorCode;
import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.internal.dto.CatalogDto;
import com.skala.supplyquote.supplyquote_backend.stub.StubData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부 - 카탈로그·재고", description = "business MCP용: 표준 모델·옵션, 옵션 적용 BOM, 부품, 재고, 대체 부품 조회. docker network 안에서만 열린다.")
@RestController
@RequestMapping("/internal")
public class InternalCatalogController {

    private final StubData stub;

    public InternalCatalogController(StubData stub) {
        this.stub = stub;
    }

    @Operation(summary = "표준 모델 목록", description = "[STUB] 표준 모델 3종과 붙일 수 있는 옵션(model_options). 사양 비교(match_spec)의 기준값이다.")
    @GetMapping("/models")
    public List<CatalogDto.ModelResponse> models() {
        return stub.models();
    }

    @Operation(summary = "표준 모델 조회", description = "[STUB]")
    @ApiResponse(responseCode = "404", description = "없는 모델",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/models/{modelId}")
    public CatalogDto.ModelResponse model(@PathVariable String modelId) {
        return stub.models().stream().filter(m -> m.modelId().equals(modelId)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "없는 모델입니다: " + modelId));
    }

    @Operation(summary = "옵션 적용 BOM 조회",
            description = "[STUB] 모델의 기본 BOM에 옵션을 적용한 결과(5_sg2_supply.json의 bom_effective). 옵션의 replaces 부품은 빠지고 uses 부품이 들어간다. "
                    + "예) EWP-12-B + OPT-HT,OPT-IP9K → P-MCU-STD 대신 P-MCU-G1, P-SEAL-STD 대신 P-SEAL-9K. "
                    + "options를 비우면 기본 BOM이다. 모델에 붙일 수 없는 옵션이면 400. (스텁은 EWP-12-B + 두 옵션만 응답한다)")
    @ApiResponse(responseCode = "400", description = "모델에 붙일 수 없는 옵션",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/models/{modelId}/bom")
    public CatalogDto.BomResponse bom(
            @PathVariable String modelId,
            @Parameter(description = "쉼표로 구분한 옵션 코드", example = "OPT-HT,OPT-IP9K")
            @RequestParam(name = "options", required = false) List<String> options) {
        return new CatalogDto.BomResponse(modelId, List.of("OPT-HT", "OPT-IP9K"), stub.bomEffective());
    }

    @Operation(summary = "부품 마스터 조회", description = "[STUB] 공급사, 개당 원가, 신규 발주 조달기간, 핵심 부품 여부. 견적 원가 계산(calc_cost)과 대안 분석에 쓴다.")
    @ApiResponse(responseCode = "404", description = "없는 부품",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/parts/{partId}")
    public CatalogDto.PartResponse part(@PathVariable String partId) {
        return stub.parts().stream().filter(p -> p.partId().equals(partId)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "없는 부품입니다: " + partId));
    }

    @Operation(summary = "부품 재고·입고 예정 조회",
            description = "[STUB] available은 기존 주문 배정분(allocated)을 뺀 가용 재고(on_hand - allocated)이다. 입고 예정은 부품당 1건. "
                    + "예) P-MCU-G1: 실물 9,000, 배정 7,500 → 가용 1,500, 입고 5,000개(2027-01-20). (스텁은 BOM 부품 6종만 응답한다)")
    @ApiResponse(responseCode = "404", description = "없는 부품",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/parts/{partId}/inventory")
    public CatalogDto.InventoryResponse inventory(@PathVariable String partId) {
        var inv = stub.inventory(partId);
        if (inv == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "스텁에 재고가 없는 부품입니다: " + partId);
        }
        return inv;
    }

    @Operation(summary = "대체 부품 후보 조회",
            description = "[STUB] 대체 가능한 부품과 검증 시험 일수, 핀 호환 여부, 근거 청크, 개당 원가 차이, 대체 부품의 가용 재고. 대체 부품이 없으면 빈 목록.")
    @GetMapping("/parts/{partId}/substitutes")
    public List<CatalogDto.SubstituteResponse> substitutes(@PathVariable String partId) {
        return "P-MCU-G1".equals(partId) ? List.of(stub.substitute()) : List.of();
    }
}
