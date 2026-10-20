package com.skala.supplyquote.supplyquote_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.skala.supplyquote.supplyquote_backend.domain.AlternativeType;
import com.skala.supplyquote.supplyquote_backend.dto.QuoteParts;
import com.skala.supplyquote.supplyquote_backend.dto.ReviewParts;
import com.skala.supplyquote.supplyquote_backend.internal.dto.CatalogDto;
import com.skala.supplyquote.supplyquote_backend.internal.dto.QuoteInternalDto;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 데이터셋 정답지(05_expected, 스텁 폴더에 수정 없이 복사)가 API DTO에 **엄격하게** 매핑되는지,
 * 그리고 정답지 안의 숫자들이 서로 맞아떨어지는지 확인한다. DB가 필요 없다.
 * 정답지나 DTO가 바뀌어 어긋나면 가장 먼저 여기서 실패한다.
 */
class DatasetFixtureTests {

    /** 모르는 필드가 있으면 실패하는 엄격한 매퍼 (스프링 기본은 무시한다). */
    private final JsonMapper strict = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private JsonNode fixture(String file) throws IOException {
        try (InputStream in = new ClassPathResource("stub/" + file).getInputStream()) {
            return strict.readTree(in);
        }
    }

    private <T> List<T> list(JsonNode n, Class<T> type) {
        List<T> out = new ArrayList<>();
        n.forEach(e -> out.add(strict.treeToValue(e, type)));
        return out;
    }

    // ------------------------------------------------------------------ DTO 매핑
    @Test
    void validationIssuesMapToReviewParts() throws IOException {
        JsonNode f = fixture("2_validation_issues.json");
        List<ReviewParts.Issue> issues = list(f.get("issues"), ReviewParts.Issue.class);
        assertThat(issues).hasSize(7);
        assertThat(issues).filteredOn(i -> "HIGH".equals(i.severity())).hasSize(1);
    }

    @Test
    void outputStateMapsToAssumptionsAndContractRisks() throws IOException {
        JsonNode f = fixture("4_output_state.json");
        assertThat(list(f.get("assumptions"), ReviewParts.Assumption.class)).hasSize(3);
        assertThat(list(f.get("contract_risks"), ReviewParts.ContractRisk.class)).hasSize(2);
        assertThat(f.get("customer_questions")).hasSize(5);
    }

    @Test
    void sg2SupplyMapsToBomShortagesAndAlternatives() throws IOException {
        JsonNode f = fixture("5_sg2_supply.json");
        assertThat(list(f.get("bom_effective"), CatalogDto.BomLine.class)).hasSize(6);
        assertThat(list(f.get("shortages"), ReviewParts.Shortage.class)).hasSize(1);
        List<ReviewParts.Alternative> alts = list(f.get("alternatives"), ReviewParts.Alternative.class);
        assertThat(alts).extracting(ReviewParts.Alternative::type).containsExactly(
                AlternativeType.WAIT_INCOMING, AlternativeType.PARTIAL_DELIVERY,
                AlternativeType.SUBSTITUTE_PART, AlternativeType.NEW_ORDER);
    }

    @Test
    void sg3QuoteDraftMapsToQuoteDraftRequest() throws IOException {
        // quote_id와 status는 서버가 정하므로 요청 DTO에는 없다
        ObjectNode draft = (ObjectNode) fixture("6_sg3_quote.json").get("quote_draft").deepCopy();
        assertThat(draft.get("quote_id").isNull()).isTrue();
        draft.remove("quote_id");
        draft.remove("status");
        var req = strict.treeToValue(draft, QuoteInternalDto.QuoteDraftRequest.class);
        assertThat(req.modelId()).isEqualTo("EWP-12-B");
        assertThat(req.priceByYear()).hasSize(6);
        assertThat(req.checks()).hasSize(5).allMatch(c -> "PASS".equals(c.result()));
        assertThat(req.conditions()).extracting(QuoteParts.Condition::type)
                .contains("ASSUMPTION", "SUBSTITUTE_PART", "CONTRACT_PROPOSAL");
    }

    // ------------------------------------------------------------------ 정답지 내부 정합성
    @Test
    void alternativesAreConsistent() throws IOException {
        JsonNode f = fixture("5_sg2_supply.json");
        List<ReviewParts.Alternative> alts = list(f.get("alternatives"), ReviewParts.Alternative.class);

        // 추천은 정확히 하나이고 recommended_alt와 같은 유형이다
        assertThat(alts).filteredOn(a -> Boolean.TRUE.equals(a.recommended())).hasSize(1);
        var recommended = alts.stream().filter(a -> Boolean.TRUE.equals(a.recommended())).findFirst().orElseThrow();
        assertThat(recommended.type().name()).isEqualTo(f.get("recommended_alt").asString());
        // 추천안은 납기를 맞추고 실행 가능하다
        assertThat(recommended.meetsDueDate()).isTrue();
        assertThat(recommended.feasible()).isTrue();

        // 대체 부품 추가 비용 = 대체 수량 x 개당 원가 차이 = 500 x 300
        assertThat(recommended.extraCostKrw()).isEqualTo(recommended.coveredQty() * recommended.unitCostDiffKrw().longValue());
        assertThat(recommended.extraCostKrw()).isEqualTo(150000L);

        // 납기를 못 맞추는 대안은 지연 일수가 있고, 맞추는 대안은 0이다
        for (var a : alts) {
            assertThat(a.meetsDueDate() ? a.delayDays() == 0 : a.delayDays() > 0)
                    .as("%s: meets_due_date와 delay_days가 일치", a.type()).isTrue();
        }
        // 분할 납품: 납기 내 + 납기 후 수량 = 부족 수량이 아니라 전체 필요 수량
        var partial = alts.stream().filter(a -> a.type() == AlternativeType.PARTIAL_DELIVERY).findFirst().orElseThrow();
        assertThat(partial.onTimeQty() + partial.lateQty()).isEqualTo(f.get("demand").get("sample_qty").asInt());
    }

    @Test
    void shortageAndSupplyCheckAreConsistent() throws IOException {
        JsonNode f = fixture("5_sg2_supply.json");
        LocalDate due = LocalDate.parse(f.get("demand").get("sample_due_date").asString());
        for (JsonNode c : f.get("supply_check")) {
            int available = c.get("on_hand").asInt() - c.get("allocated").asInt();
            assertThat(c.get("available").asInt()).as(c.get("part_id").asString()).isEqualTo(available);
            int shortQty = Math.max(0, c.get("required_qty").asInt() - available);
            assertThat(c.get("short_qty").asInt()).isEqualTo(shortQty);
            assertThat(c.get("status").asString()).isEqualTo(shortQty > 0 ? "SHORT" : "OK");
        }
        ReviewParts.Shortage s = list(f.get("shortages"), ReviewParts.Shortage.class).get(0);
        assertThat(s.shortQty()).isEqualTo(500);
        assertThat(s.incomingDelayDays()).isEqualTo((int) ChronoUnit.DAYS.between(due, s.incomingDate()));
        // 최종 계획: 가용분 + 대체 부품 = 시작품 수량
        int plan = 0;
        for (JsonNode v : f.get("final_plan")) {
            plan += v.asInt();
        }
        assertThat(plan).isEqualTo(f.get("demand").get("sample_qty").asInt());
    }

    @Test
    void quotePricesFollowTheRulesFormula() throws IOException {
        // rules.yaml: price_by_year.formula = round(unit_price * (1 - cr/100) ** (year - 1)), 1년차 = 기준가
        JsonNode q = fixture("6_sg3_quote.json").get("quote_draft");
        int unitPrice = q.get("unit_price_krw").asInt();
        int partsCost = q.get("parts_cost_krw").asInt();
        double cr = 3.0; // RFQ 연간 가격 인하

        // 단가 = 모델가 + 옵션가, 부품 원가 = 부품별 원가 합계
        int priceSum = 0;
        for (JsonNode p : q.get("price_breakdown")) {
            priceSum += p.get("amount_krw").asInt();
        }
        int costSum = 0;
        for (JsonNode p : q.get("parts_cost_breakdown")) {
            costSum += p.get("cost_krw").asInt();
        }
        assertThat(priceSum).isEqualTo(unitPrice).isEqualTo(37300);
        assertThat(costSum).isEqualTo(partsCost).isEqualTo(21650);

        // 연도별 단가·마진
        for (JsonNode y : q.get("price_by_year")) {
            int year = y.get("year").asInt();
            long expectedPrice = Math.round(unitPrice * Math.pow(1 - cr / 100, year - 1));
            assertThat(y.get("cr_applied").asInt()).as("%d년차 인하 횟수", year).isEqualTo(year - 1);
            assertThat(y.get("unit_price_krw").asLong()).as("%d년차 단가", year).isEqualTo(expectedPrice);
            double margin = Math.round((expectedPrice - partsCost) * 1000.0 / expectedPrice) / 10.0;
            assertThat(y.get("margin_pct").asDouble()).as("%d년차 마진", year).isEqualTo(margin);
            assertThat(margin).as("제16조: 마진 30%% 이상").isGreaterThanOrEqualTo(30.0);
        }
        // 6년차는 인하 5회 → 마진 32.4%
        JsonNode last = q.get("price_by_year").get(5);
        assertThat(last.get("cr_applied").asInt()).isEqualTo(5);
        assertThat(last.get("margin_pct").asDouble()).isEqualTo(32.4);
        assertThat(q.get("margin_pct").asDouble()).isEqualTo(42.0);
    }

    @Test
    void quoteFormatFollowsArticle17() throws IOException {
        // 제17조: 필수 8항목(수신처, 품목, 단가, 수량, 납기, 인도 조건, 유효기간, 조건부 사항), 유효기간 = 발행일 + 30일
        JsonNode q = fixture("6_sg3_quote.json").get("quote_draft");
        for (String required : List.of("recipient", "item", "unit_price_krw", "sample_qty", "sample_due_date",
                "incoterms", "valid_until", "conditions")) {
            assertThat(q.hasNonNull(required)).as("필수 항목 %s", required).isTrue();
        }
        LocalDate issue = LocalDate.parse(q.get("issue_date").asString());
        assertThat(LocalDate.parse(q.get("valid_until").asString())).isEqualTo(issue.plusDays(30));
    }

    @Test
    void sg4RegistrationMatchesQuoteIdFormat() throws IOException {
        JsonNode f = fixture("7_sg4_register.json");
        // Q-{발행 연도}-{발행 월일}-{그날 일련번호}
        assertThat(f.get("registration").get("quote_id").asString()).matches("Q-2026-1019-\\d{3}");
        assertThat(f.get("registration").get("status").asString()).isEqualTo("REGISTERED");
        assertThat(f.get("registration").get("rfq_status").asString()).isEqualTo("QUOTED");
        for (JsonNode r : f.get("recheck")) {
            assertThat(r.get("changed").asBoolean()).isEqualTo(r.get("available_at_sg2").asInt() != r.get("available_now").asInt());
        }
    }
}
