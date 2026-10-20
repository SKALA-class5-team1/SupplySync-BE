package com.skala.supplyquote.supplyquote_backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public final class AnalysisDto {

    private AnalysisDto() {
    }

    @Schema(description = "담당자 승인 화면의 데이터. 분석 결과 + 견적(초안 포함). MANAGER 전용")
    public record AnalysisView(
            ReviewDto.ManagerReview review,
            @Schema(description = "견적 목록. 반려·조건 변경으로 재생성되면 이전 견적(REJECTED)도 이력으로 포함된다")
            List<QuoteViews.ManagerQuote> quotes) {
    }
}
