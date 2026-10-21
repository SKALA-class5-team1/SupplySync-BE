package com.skala.supplyquote.supplyquote_backend.web.dto;

import com.skala.supplyquote.supplyquote_backend.domain.AlternativeType;
import com.skala.supplyquote.supplyquote_backend.domain.ReviewStatus;
import com.skala.supplyquote.supplyquote_backend.dto.ReviewParts;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ReviewDto {

    private ReviewDto() {
    }

    /** rfq_reviews 한 행. 내부용이라 MANAGER에게만 보여준다. */
    @Schema(description = "분석 결과 (rfq_reviews). **내부용: MANAGER 전용**, 고객에게 노출 금지")
    public record ManagerReview(
            @Schema(example = "HB-EV7-EWP-001") String rfqNumber,
            @Schema(description = "LangGraph 실행 ID", example = "task-3f2b8c1e-5a4d-4e7b-9c1a-2d6f8e0b7a11") String threadId,
            @Schema(example = "WAITING") ReviewStatus status,
            // SG1
            @Schema(example = "EWP-12-B") String selectedModel,
            @Schema(example = "[\"OPT-HT\", \"OPT-IP9K\"]") List<String> options,
            @Schema(description = "표준 | 파생 사양 | 신규 개발", example = "파생 사양") String fitType,
            List<ReviewParts.Issue> issues,
            @Schema(description = "통과한 검증 항목") List<String> passedChecks,
            List<ReviewParts.Assumption> assumptions,
            @Schema(description = "가정을 확인한 담당자(users.user_id)", example = "2") Long confirmedBy,
            LocalDateTime confirmedAt,
            @Schema(description = "고객에게 보낼 확인 질의서") List<String> customerQuestions,
            List<ReviewParts.ContractRisk> contractRisks,
            // SG2
            List<ReviewParts.Shortage> shortages,
            List<ReviewParts.Alternative> alternatives,
            @Schema(example = "SUBSTITUTE_PART") AlternativeType recommendedAlt,
            @Schema(description = "SG4 재검증용 확정 부품 수량") Map<String, Integer> finalPlan) {
    }
}
