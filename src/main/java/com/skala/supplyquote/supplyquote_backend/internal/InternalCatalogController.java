package com.skala.supplyquote.supplyquote_backend.internal;

import com.skala.supplyquote.supplyquote_backend.common.ErrorResponse;
import com.skala.supplyquote.supplyquote_backend.internal.dto.CatalogDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부 - 카탈로그", description = "business MCP용: 표준 모델·옵션, 옵션 적용 BOM, 부품, 대체 부품 조회. docker network 안에서만 열린다.")
@RestController
@RequestMapping("/internal")
public class InternalCatalogController {

    private static final CatalogDto.OptionResponse OPT_HT = new CatalogDto.OptionResponse(
            "OPT-HT", "고온 대응 (125℃)", "temp_max", "125", new BigDecimal("0.00"), 0, 2400, "P-MCU-STD", "P-MCU-G1");
    private static final CatalogDto.OptionResponse OPT_IP9K = new CatalogDto.OptionResponse(
            "OPT-IP9K", "고압 세차 방수 강화", "ip_rating", "IP6K9K", new BigDecimal("0.05"), 1, 900, "P-SEAL-STD", "P-SEAL-9K");

    @Operation(summary = "표준 모델 목록", description = "[STUB] 표준 모델과 붙일 수 있는 옵션(model_options)을 돌려준다. 사양 비교(match_spec)의 기준값이다.")
    @GetMapping("/models")
    public List<CatalogDto.ModelResponse> models() {
        return List.of(modelB());
    }

    @Operation(summary = "표준 모델 조회", description = "[STUB]")
    @ApiResponse(responseCode = "404", description = "없는 모델",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/models/{modelId}")
    public CatalogDto.ModelResponse model(@PathVariable String modelId) {
        return modelB();
    }

    @Operation(summary = "옵션 적용 BOM 조회",
            description = "[STUB] 모델의 기본 BOM에 옵션을 적용한 결과를 돌려준다. 옵션의 replaces 부품은 빠지고 uses 부품이 들어간다. "
                    + "예) EWP-12-B + OPT-HT,OPT-IP9K → 제어칩 P-MCU-STD 대신 P-MCU-G1, 실링 P-SEAL-STD 대신 P-SEAL-9K. "
                    + "options를 비우면 기본 BOM이다. 모델에 붙일 수 없는 옵션이면 400.")
    @ApiResponse(responseCode = "400", description = "모델에 붙일 수 없는 옵션",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/models/{modelId}/bom")
    public CatalogDto.BomResponse bom(
            @PathVariable String modelId,
            @Parameter(description = "쉼표로 구분한 옵션 코드", example = "OPT-HT,OPT-IP9K")
            @RequestParam(required = false) List<String> options) {
        return new CatalogDto.BomResponse(modelId, List.of("OPT-HT", "OPT-IP9K"), List.of(
                new CatalogDto.BomLine("P-MTR-BL12", "모터", "한결모터", 1, 9500, 45, true, "BASE"),
                new CatalogDto.BomLine("P-IMP-25", "임펠러 (회전 날개)", "대성 자체", 1, 1200, 14, false, "BASE"),
                new CatalogDto.BomLine("P-HSG-AL", "알루미늄 케이스", "동명금속", 1, 4100, 30, false, "BASE"),
                new CatalogDto.BomLine("P-CON-4P", "4핀 커넥터", "케이커넥트", 1, 600, 21, false, "BASE"),
                new CatalogDto.BomLine("P-MCU-G1", "제어칩 (고온용)", "세온반도체", 1, 5300, 120, true, "OPT-HT"),
                new CatalogDto.BomLine("P-SEAL-9K", "방수 실링 (강화)", "우성고무", 1, 950, 21, false, "OPT-IP9K")),
                List.of("P-MCU-STD", "P-SEAL-STD"));
    }

    @Operation(summary = "부품 마스터 조회", description = "[STUB] 공급사, 개당 원가, 신규 발주 리드타임, 핵심 부품 여부. 대안의 원가·납기 계산에 쓴다.")
    @ApiResponse(responseCode = "404", description = "없는 부품",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/parts/{partId}")
    public CatalogDto.PartResponse part(@PathVariable String partId) {
        return new CatalogDto.PartResponse(partId, "제어칩 (고온용)", "세온반도체", 5300, 120, true);
    }

    @Operation(summary = "대체 부품 후보 조회",
            description = "[STUB] 대체 가능한 부품과 검증 시험 일수, 개당 원가 차이, 대체 부품의 가용 재고. 선택하면 사양 재검증이 필요할 수 있다.")
    @GetMapping("/parts/{partId}/substitutes")
    public List<CatalogDto.SubstituteResponse> substitutes(@PathVariable String partId) {
        return List.of(new CatalogDto.SubstituteResponse(partId, "P-MCU-G1-ALT", "제어칩 (고온용, 대체)", "누리칩스",
                "같은 고온 등급, 핀 배치 동일로 회로 변경 불필요. 처음 쓰는 공급사라 검증 시험 필요", 14, 300, 3000, true));
    }

    private static CatalogDto.ModelResponse modelB() {
        return new CatalogDto.ModelResponse("EWP-12-B", "중형 워터펌프", new BigDecimal("25.0"), 80,
                new BigDecimal("9.0"), new BigDecimal("16.0"), -40, 110, "IP67", 42, "LIN,PWM",
                110, 90, 90, new BigDecimal("0.70"), 34000, List.of(OPT_HT, OPT_IP9K));
    }
}
