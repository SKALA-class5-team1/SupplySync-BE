package com.skala.supplyquote.supplyquote_backend.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.skala.supplyquote.supplyquote_backend.domain.RfqStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public final class InternalRfqDto {

    private InternalRfqDto() {
    }

    @Schema(description = "RFQ 정보 (Agent 조회용). 추출 컬럼은 SG1이 채우기 전에는 null")
    public record RfqInfo(
            Long rfqId,
            @Schema(example = "HB-EV7-EWP-001") String rfqNumber,
            Long customerCompanyId,
            @Schema(example = "전동식 워터펌프 (EWP, 12V)") String item,
            @Schema(example = "RECEIVED") RfqStatus status,
            @Schema(description = "RFQ 원문 파일 경로 (원문은 /content로 조회한다)", example = "01_rfq/RFQ_HB-EV7-EWP-001.md") String filePath,
            Integer eau,
            Integer sampleQty,
            LocalDate sampleDueDate,
            LocalDate sopDate,
            Integer targetPriceKrw,
            LocalDate quoteDueDate) {
    }

    /** 전달한 필드만 갱신한다 (부분 갱신). SG1이 요청서를 읽은 뒤 추출 값을 채울 때 쓴다. */
    @Schema(description = "RFQ 추출 컬럼과 상태의 부분 갱신. 전달한 필드만 바뀐다.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RfqPatch(
            @Schema(description = "추출이 끝나면 IN_REVIEW", example = "IN_REVIEW") RfqStatus status,
            @Schema(example = "120000") Integer eau,
            @Schema(example = "2000") Integer sampleQty,
            @Schema(example = "2026-12-15") LocalDate sampleDueDate,
            @Schema(example = "2027-09-01") LocalDate sopDate,
            @Schema(example = "38000") Integer targetPriceKrw,
            @Schema(example = "2026-10-30") LocalDate quoteDueDate) {
    }
}
