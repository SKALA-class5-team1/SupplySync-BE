package com.skala.supplyquote.supplyquote_backend.internal;

import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.internal.dto.CatalogDto;
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

@Tag(name = "내부 - 카탈로그·재고", description = "business MCP용: 표준 모델·옵션, 옵션 적용 BOM, 부품, 재고, 대체 부품 조회. 내부 API 인증은 다음 단계에서 적용한다.")
@RestController
@RequestMapping("/internal")
public class InternalCatalogController {

    private final CatalogQueryService catalog;

    public InternalCatalogController(CatalogQueryService catalog) {
        this.catalog = catalog;
    }

    @Operation(summary = "표준 모델 목록", description = "DB의 표준 모델과 붙일 수 있는 옵션(model_options). 사양 비교(match_spec)의 기준값이다.")
    @GetMapping("/models")
    public List<CatalogDto.ModelResponse> models() {
        return catalog.models();
    }

    @Operation(summary = "표준 모델 조회")
    @ApiResponse(responseCode = "404", description = "없는 모델",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/models/{modelId}")
    public CatalogDto.ModelResponse model(@PathVariable String modelId) {
        return catalog.model(modelId);
    }

    @Operation(summary = "옵션 적용 BOM 조회",
            description = "기본 BOM에 선택한 옵션의 부품 교체를 적용한다. options가 없으면 기본 BOM이다. 모델에 붙일 수 없는 옵션이면 400.")
    @ApiResponse(responseCode = "400", description = "모델에 붙일 수 없는 옵션",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/models/{modelId}/bom")
    public CatalogDto.BomResponse bom(
            @PathVariable String modelId,
            @Parameter(description = "쉼표로 구분한 옵션 코드", example = "OPT-HT,OPT-IP9K")
            @RequestParam(name = "options", required = false) List<String> options) {
        return catalog.bom(modelId, options);
    }

    @Operation(summary = "부품 마스터 조회", description = "공급사, 개당 원가, 신규 발주 조달기간, 핵심 부품 여부.")
    @ApiResponse(responseCode = "404", description = "없는 부품",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/parts/{partId}")
    public CatalogDto.PartResponse part(@PathVariable String partId) {
        return catalog.part(partId);
    }

    @Operation(summary = "부품 재고·입고 예정 조회",
            description = "available은 on_hand - allocated이다. 입고 예정은 부품당 1건이다.")
    @ApiResponse(responseCode = "404", description = "없는 부품",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/parts/{partId}/inventory")
    public CatalogDto.InventoryResponse inventory(@PathVariable String partId) {
        return catalog.inventory(partId);
    }

    @Operation(summary = "대체 부품 후보 조회",
            description = "대체 부품의 검증 기간, 핀 호환, 근거 청크, 원가 차이와 가용 재고. 후보가 없으면 빈 목록.")
    @GetMapping("/parts/{partId}/substitutes")
    public List<CatalogDto.SubstituteResponse> substitutes(@PathVariable String partId) {
        return catalog.substitutes(partId);
    }
}
