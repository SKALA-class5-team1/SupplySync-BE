package com.skala.supplyquote.supplyquote_backend.stub;

import com.skala.supplyquote.supplyquote_backend.domain.AlternativeType;
import com.skala.supplyquote.supplyquote_backend.domain.QuoteStatus;
import com.skala.supplyquote.supplyquote_backend.domain.ReviewStatus;
import com.skala.supplyquote.supplyquote_backend.dto.QuoteParts;
import com.skala.supplyquote.supplyquote_backend.dto.ReviewParts;
import com.skala.supplyquote.supplyquote_backend.internal.dto.CatalogDto;
import com.skala.supplyquote.supplyquote_backend.internal.dto.QuoteInternalDto;
import com.skala.supplyquote.supplyquote_backend.web.dto.QuoteViews;
import com.skala.supplyquote.supplyquote_backend.web.dto.ReviewDto;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 스텁 응답의 재료. 데이터셋 정답지(src/main/resources/stub/*.json, 05_expected를 수정 없이 복사)를 DTO로 읽어서 만든다.
 * 정답지와 DTO가 어긋나면 여기서 바로 실패하므로 계약 검증도 겸한다. 실제 구현이 들어오면 삭제한다.
 */
@Component
public class StubData {

    public static final String RFQ_NUMBER = "HB-EV7-EWP-001";
    public static final String THREAD_ID = "task-3f2b8c1e-5a4d-4e7b-9c1a-2d6f8e0b7a11";
    public static final String QUOTE_ID = "Q-2026-1019-001";

    private final JsonMapper mapper;

    public StubData(JsonMapper mapper) {
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- 정답지 읽기
    public JsonNode node(String file) {
        try (InputStream in = new ClassPathResource("stub/" + file).getInputStream()) {
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> T value(JsonNode n, Class<T> type) {
        return mapper.treeToValue(n, type);
    }

    private <T> List<T> list(JsonNode n, Class<T> type) {
        List<T> out = new ArrayList<>();
        n.forEach(e -> out.add(value(e, type)));
        return out;
    }

    private List<String> strings(JsonNode n) {
        List<String> out = new ArrayList<>();
        n.forEach(e -> out.add(e.asString()));
        return out;
    }

    // ---------------------------------------------------------------- 분석 결과 (rfq_reviews)
    public ReviewDto.ManagerReview review() {
        JsonNode issues = node("2_validation_issues.json");
        JsonNode state = node("4_output_state.json");
        JsonNode supply = node("5_sg2_supply.json");
        return new ReviewDto.ManagerReview(
                RFQ_NUMBER, THREAD_ID, ReviewStatus.WAITING,
                state.get("selected_model").asString(),
                strings(state.get("options")),
                state.get("fit_type").asString(),
                list(issues.get("issues"), ReviewParts.Issue.class),
                strings(issues.get("passed_checks")),
                list(state.get("assumptions"), ReviewParts.Assumption.class),
                2L, LocalDateTime.parse("2026-10-19T10:30:00"),
                strings(state.get("customer_questions")),
                list(state.get("contract_risks"), ReviewParts.ContractRisk.class),
                list(supply.get("shortages"), ReviewParts.Shortage.class),
                list(supply.get("alternatives"), ReviewParts.Alternative.class),
                AlternativeType.valueOf(supply.get("recommended_alt").asString()));
    }

    // ---------------------------------------------------------------- 견적 (quotes)
    public QuoteInternalDto.QuoteDraftRequest quoteDraft() {
        return value(node("6_sg3_quote.json").get("quote_draft"), QuoteInternalDto.QuoteDraftRequest.class);
    }

    public QuoteViews.ManagerQuote managerQuote(QuoteStatus status) {
        var d = quoteDraft();
        boolean approved = status == QuoteStatus.APPROVED || status == QuoteStatus.REGISTERED;
        return new QuoteViews.ManagerQuote(
                QUOTE_ID, d.rfqNumber(), d.modelId(), d.options(), d.unitPriceKrw(), d.partsCostKrw(), d.marginPct(),
                d.priceByYear(), d.sampleQty(), d.sampleDueDate(), d.validUntil(), d.conditions(), d.checks(), status,
                approved ? 2L : null,
                approved ? LocalDateTime.parse("2026-10-19T11:00:00") : null,
                null,
                status == QuoteStatus.REGISTERED ? LocalDateTime.parse("2026-10-19T11:05:00") : null,
                LocalDateTime.parse("2026-10-19T10:50:00"));
    }

    public QuoteViews.CustomerQuote customerQuote() {
        var d = quoteDraft();
        return new QuoteViews.CustomerQuote(
                QUOTE_ID, d.rfqNumber(), d.modelId(), d.options(), d.unitPriceKrw(),
                d.priceByYear().stream().map(p -> new QuoteParts.CustomerPriceByYear(p.year(), p.unitPriceKrw())).toList(),
                d.sampleQty(), d.sampleDueDate(), d.validUntil(), d.conditions(), QuoteStatus.REGISTERED);
    }

    // ---------------------------------------------------------------- 카탈로그 (BOM, 재고)
    public List<CatalogDto.BomLine> bomEffective() {
        return list(node("5_sg2_supply.json").get("bom_effective"), CatalogDto.BomLine.class);
    }

    /** 5_sg2_supply.json의 supply_check에서 부품의 재고를 찾는다. BOM 부품 6종만 있다. */
    public CatalogDto.InventoryResponse inventory(String partId) {
        for (JsonNode n : node("5_sg2_supply.json").get("supply_check")) {
            if (partId.equals(n.get("part_id").asString())) {
                return new CatalogDto.InventoryResponse(partId, n.get("on_hand").asInt(), n.get("allocated").asInt(),
                        n.get("available").asInt(), n.get("incoming_qty").asInt(),
                        n.get("incoming_date").isNull() ? null : java.time.LocalDate.parse(n.get("incoming_date").asString()));
            }
        }
        return null;
    }

    // 데이터셋 시드(02_seed.sql)의 값. 스텁이 시드와 같은 값을 돌려주도록 옮겨 두었다.
    public List<CatalogDto.PartResponse> parts() {
        return List.of(
                new CatalogDto.PartResponse("P-MTR-BL12", "모터", "한결모터", 9500, 45, true),
                new CatalogDto.PartResponse("P-MCU-STD", "제어칩 (표준)", "세온반도체", 3200, 60, true),
                new CatalogDto.PartResponse("P-MCU-G1", "제어칩 (고온용)", "세온반도체", 5300, 120, true),
                new CatalogDto.PartResponse("P-MCU-G1-ALT", "제어칩 (고온용, 대체)", "누리칩스", 5600, 30, true),
                new CatalogDto.PartResponse("P-IMP-25", "임펠러", "대성 자체", 1200, 14, false),
                new CatalogDto.PartResponse("P-HSG-AL", "알루미늄 케이스", "동명금속", 4100, 30, false),
                new CatalogDto.PartResponse("P-SEAL-STD", "방수 실링 (표준)", "우성고무", 400, 21, false),
                new CatalogDto.PartResponse("P-SEAL-9K", "방수 실링 (강화)", "우성고무", 950, 21, false),
                new CatalogDto.PartResponse("P-CON-4P", "4핀 커넥터", "케이커넥트", 600, 21, false));
    }

    public List<CatalogDto.ModelResponse> models() {
        var ht = new CatalogDto.OptionResponse("OPT-HT", "고온 대응 (125℃)", "temp_max", "125",
                new BigDecimal("0.00"), 0, 2400, "P-MCU-STD", "P-MCU-G1");
        var ip = new CatalogDto.OptionResponse("OPT-IP9K", "방수 강화 (IP6K9K)", "ip_rating", "IP6K9K",
                new BigDecimal("0.05"), 1, 900, "P-SEAL-STD", "P-SEAL-9K");
        return List.of(
                new CatalogDto.ModelResponse("EWP-12-A", "소형 워터펌프", new BigDecimal("15.0"), 50, new BigDecimal("9"),
                        new BigDecimal("16"), -40, 105, "IP67", 43, "PWM", 100, 85, 80, new BigDecimal("0.55"), 29000, List.of(ht)),
                new CatalogDto.ModelResponse("EWP-12-B", "중형 워터펌프", new BigDecimal("25.0"), 80, new BigDecimal("9"),
                        new BigDecimal("16"), -40, 110, "IP67", 42, "LIN,PWM", 110, 90, 90, new BigDecimal("0.70"), 34000, List.of(ht, ip)),
                new CatalogDto.ModelResponse("EWP-12-C", "대형 워터펌프", new BigDecimal("35.0"), 100, new BigDecimal("9"),
                        new BigDecimal("16"), -40, 125, "IP6K9K", 48, "LIN", 118, 98, 94, new BigDecimal("0.95"), 45000, List.of()));
    }

    public CatalogDto.SubstituteResponse substitute() {
        return new CatalogDto.SubstituteResponse("P-MCU-G1", "P-MCU-G1-ALT", "제어칩 (고온용, 대체)", "누리칩스",
                14, true, "alt-002", 300, 3000);
    }

    // ---------------------------------------------------------------- RFQ 원문
    public String rfqContent() {
        try (InputStream in = new ClassPathResource("db/seed/01_rfq/RFQ_HB-EV7-EWP-001.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
