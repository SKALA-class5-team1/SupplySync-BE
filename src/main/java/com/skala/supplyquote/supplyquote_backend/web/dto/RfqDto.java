package com.skala.supplyquote.supplyquote_backend.web.dto;

import com.skala.supplyquote.supplyquote_backend.domain.RfqStatus;
import com.skala.supplyquote.supplyquote_backend.domain.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public final class RfqDto {

    private RfqDto() {
    }

    @Schema(description = "RFQ 접수 결과. 분석은 비동기로 시작된다.")
    public record UploadAccepted(
            @Schema(example = "1") Long rfqId,
            @Schema(example = "HB-EV7-EWP-001") String rfqNumber,
            @Schema(description = "진행 상황은 /api/ai/tasks/{taskId}/stream 으로 구독한다",
                    example = "3f2b8c1e-5a4d-4e7b-9c1a-2d6f8e0b7a11") UUID taskId,
            @Schema(example = "QUEUED") TaskStatus status) {
    }

    @Schema(description = "RFQ 목록 항목")
    public record Summary(
            @Schema(example = "1") Long rfqId,
            @Schema(example = "HB-EV7-EWP-001") String rfqNumber,
            @Schema(example = "한별모터스") String customerCompany,
            @Schema(example = "전동식 워터펌프 (EWP, 12V)") String item,
            @Schema(example = "IN_REVIEW") RfqStatus status,
            @Schema(example = "2026-10-12T09:00:00") LocalDateTime receivedAt) {
    }

    @Schema(description = "RFQ 상세. 추출 컬럼(eau 이하)은 SG1이 요청서를 읽은 뒤에 채워진다.")
    public record Detail(
            Long rfqId,
            String rfqNumber,
            Long customerCompanyId,
            String customerCompany,
            String item,
            RfqStatus status,
            LocalDateTime receivedAt,
            @Schema(description = "연간 물량", example = "120000") Integer eau,
            @Schema(description = "시작품 수량", example = "2000") Integer sampleQty,
            @Schema(description = "고객 납기(시작품)", example = "2026-12-15") LocalDate sampleDueDate,
            @Schema(description = "양산 시작", example = "2027-09-01") LocalDate sopDate,
            @Schema(example = "38000") Integer targetPriceKrw,
            @Schema(example = "2026-10-30") LocalDate quoteDueDate,
            @Schema(description = "가장 최근 분석 작업") UUID latestTaskId) {
    }
}
