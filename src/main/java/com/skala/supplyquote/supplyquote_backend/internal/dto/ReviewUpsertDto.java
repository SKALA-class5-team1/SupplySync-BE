package com.skala.supplyquote.supplyquote_backend.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.skala.supplyquote.supplyquote_backend.domain.AlternativeType;
import com.skala.supplyquote.supplyquote_backend.domain.ReviewStatus;
import com.skala.supplyquote.supplyquote_backend.dto.ReviewParts;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class ReviewUpsertDto {

    private ReviewUpsertDto() {
    }

    /**
     * (rfqNumber, threadId)로 upsert한다. **전달한 섹션만 갱신하고 나머지는 유지**한다.
     * SG1이 issues, selectedModel 등을 먼저 저장하고, SG2가 이어서 shortages, alternatives를 저장한다.
     */
    @Schema(description = "분석 결과 upsert. 같은 (rfq_number, thread_id)로 여러 번 호출하면 전달한 섹션만 갱신된다.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReviewUpsertRequest(
            @NotBlank @Size(max = 30) @Schema(example = "HB-EV7-EWP-001") String rfqNumber,
            @NotBlank @Size(max = 64) @Schema(description = "백엔드가 Agent 실행 요청에 실어 보낸 값", example = "task-3f2b8c1e-5a4d-4e7b-9c1a-2d6f8e0b7a11") String threadId,
            @Schema(example = "WAITING") ReviewStatus status,
            // SG1
            @Schema(example = "EWP-12-B") String selectedModel,
            @Schema(example = "[\"OPT-HT\", \"OPT-IP9K\"]") List<String> options,
            @Schema(example = "파생 사양") String fitType,
            List<ReviewParts.Issue> issues,
            List<String> passedChecks,
            List<ReviewParts.Assumption> assumptions,
            List<String> customerQuestions,
            List<ReviewParts.ContractRisk> contractRisks,
            // SG2
            List<ReviewParts.Shortage> shortages,
            List<ReviewParts.Alternative> alternatives,
            @Schema(example = "SUBSTITUTE_PART") AlternativeType recommendedAlt) {
    }

    @Schema(description = "upsert 결과")
    public record ReviewUpsertResponse(
            @Schema(example = "1") Long reviewId,
            String rfqNumber,
            String threadId,
            ReviewStatus status,
            LocalDateTime updatedAt) {
    }
}
