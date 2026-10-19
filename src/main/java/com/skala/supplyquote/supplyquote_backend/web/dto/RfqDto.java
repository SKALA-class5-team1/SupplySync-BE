package com.skala.supplyquote.supplyquote_backend.web.dto;

import com.skala.supplyquote.supplyquote_backend.domain.RfqStatus;
import com.skala.supplyquote.supplyquote_backend.domain.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class RfqDto {

    private RfqDto() {
    }

    @Schema(description = "RFQ 등록 요청. 수량·납기·사양은 요청서 원문에서 Agent가 추출하므로 따로 받지 않는다.")
    public record CreateRequest(
            @NotBlank @Size(max = 200)
            @Schema(example = "HB-EV7 전동식 워터펌프 견적 요청") String title,
            @NotBlank
            @Schema(description = "고객 요청서 원문(마크다운 또는 텍스트)",
                    example = "# 견적요청서 (RFQ)\n| RFQ 번호 | HB-EV7-EWP-001 |\n...\n유량 22 L/h 이상 (수두 70 kPa 기준)\n...") String content,
            @Size(max = 30)
            @Schema(description = "요청서에 적힌 RFQ 번호. 비워 두면 Agent가 원문에서 추출해 채운다", example = "HB-EV7-EWP-001") String rfqNumber) {
    }

    @Schema(description = "RFQ 접수 결과. 분석은 비동기로 시작된다.")
    public record AcceptedResponse(
            @Schema(example = "101") Long rfqId,
            @Schema(description = "진행 상황은 /api/ai/tasks/{taskId}/stream 으로 구독한다",
                    example = "3f2b8c1e-5a4d-4e7b-9c1a-2d6f8e0b7a11") UUID taskId,
            @Schema(example = "QUEUED") TaskStatus status) {
    }

    @Schema(description = "RFQ 목록 항목")
    public record Summary(
            @Schema(example = "101") Long rfqId,
            @Schema(example = "HB-EV7-EWP-001") String rfqNumber,
            @Schema(example = "HB-EV7 전동식 워터펌프 견적 요청") String title,
            @Schema(example = "WAITING_APPROVAL") RfqStatus status,
            Instant createdAt) {
    }

    @Schema(description = "RFQ 상세")
    public record Detail(
            Long rfqId,
            String rfqNumber,
            String title,
            String content,
            RfqStatus status,
            @Schema(description = "가장 최근 분석 작업") UUID latestTaskId,
            Instant createdAt) {
    }
}
