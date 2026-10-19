package com.skala.supplyquote.supplyquote_backend.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public final class SupplyDto {

    private SupplyDto() {
    }

    @Schema(description = "부품 재고. available = onHand - allocated (기존 주문 배정분 제외). 입고 예정은 부품당 1건이다.")
    public record InventoryResponse(
            @Schema(example = "P-MCU-G1") String partId,
            @Schema(description = "창고 실물 재고", example = "9000") Integer onHand,
            @Schema(description = "기존 주문에 이미 배정된 수량", example = "7500") Integer allocated,
            @Schema(description = "가용 재고", example = "1500") Integer available,
            @Schema(description = "입고 예정 수량", example = "5000") Integer incomingQty,
            @Schema(description = "입고 예정일. 없으면 null", example = "2027-01-20") LocalDate incomingDate) {
    }
}
