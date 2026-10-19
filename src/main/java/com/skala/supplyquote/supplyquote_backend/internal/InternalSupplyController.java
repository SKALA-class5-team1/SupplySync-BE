package com.skala.supplyquote.supplyquote_backend.internal;

import com.skala.supplyquote.supplyquote_backend.internal.dto.SupplyDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부 - 재고", description = "business MCP용: 부품 재고와 입고 예정 조회")
@RestController
@RequestMapping("/internal/parts")
public class InternalSupplyController {

    @Operation(summary = "부품 재고·입고 예정 조회",
            description = "[STUB] available은 기존 주문 배정분(allocated)을 뺀 가용 재고이다. 부족 분석은 이 값을 기준으로 한다. "
                    + "예) P-MCU-G1: 실물 9,000, 배정 7,500 → 가용 1,500, 입고 예정 5,000개(2027-01-20).")
    @GetMapping("/{partId}/inventory")
    public SupplyDto.InventoryResponse inventory(@PathVariable String partId) {
        return new SupplyDto.InventoryResponse(partId, 9000, 7500, 1500, 5000, LocalDate.parse("2027-01-20"));
    }
}
