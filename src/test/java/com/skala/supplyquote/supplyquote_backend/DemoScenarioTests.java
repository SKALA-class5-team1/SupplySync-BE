package com.skala.supplyquote.supplyquote_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 지영님 데이터셋(RFQ HB-EV7-EWP-001)의 정답이 시드 DB에서 그대로 재현되는지 확인한다.
 * 시드나 스키마가 바뀌어 데모 시나리오가 깨지면 여기서 먼저 알 수 있다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"test", "demo"})
class DemoScenarioTests {

    private static final int SAMPLE_QTY = 2000;
    private static final LocalDate SAMPLE_DUE = LocalDate.parse("2026-12-15");

    @Autowired
    JdbcTemplate jdbc;

    private int count(String table) {
        Integer n = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void seedHasExpectedRowCounts() {
        assertThat(count("models")).isEqualTo(3);
        assertThat(count("options")).isEqualTo(2);
        assertThat(count("model_options")).isEqualTo(3);
        assertThat(count("parts")).isEqualTo(9);
        assertThat(count("bom")).isEqualTo(12);
        assertThat(count("inventory")).isEqualTo(9);
        assertThat(count("part_substitutes")).isEqualTo(1);
        assertThat(count("doc_chunks")).isEqualTo(22);
        assertThat(count("users")).isEqualTo(2);
    }

    @Test
    void recommendedModelWithOptionsMeetsTargetPrice() {
        // EWP-12-B + OPT-HT + OPT-IP9K = 34,000 + 2,400 + 900 = 37,300원 (목표 38,000원 이내)
        Map<String, Object> row = jdbc.queryForMap("""
                select m.base_price_krw + sum(o.add_price_krw) as price,
                       m.weight_kg + sum(o.add_weight_kg)      as weight,
                       m.noise_db + sum(o.add_noise_db)        as noise
                from models m
                join model_options mo on mo.model_id = m.model_id
                join options o on o.option_code = mo.option_code
                where m.model_id = 'EWP-12-B' and o.option_code in ('OPT-HT', 'OPT-IP9K')
                group by m.base_price_krw, m.weight_kg, m.noise_db""");
        assertThat(((Number) row.get("price")).intValue()).isEqualTo(37300);
        assertThat(((Number) row.get("weight")).doubleValue()).isEqualTo(0.75);
        assertThat(((Number) row.get("noise")).intValue()).isEqualTo(43);
    }

    @Test
    void optionsReplaceBomParts() {
        List<String> applied = jdbc.queryForList("""
                select part_id from (
                    select part_id from bom
                    where model_id = 'EWP-12-B'
                      and part_id not in (select replaces_part_id from options where option_code in ('OPT-HT','OPT-IP9K'))
                    union all
                    select uses_part_id from options where option_code in ('OPT-HT','OPT-IP9K')
                ) t""", String.class);
        assertThat(applied).contains("P-MCU-G1", "P-SEAL-9K").doesNotContain("P-MCU-STD", "P-SEAL-STD");
        assertThat(applied).hasSize(6);
    }

    @Test
    void highTemperatureChipIsShortAndInboundIsTooLate() {
        // 가용 재고 = on_hand - allocated = 9,000 - 7,500 = 1,500 → 시작품 2,000개에 500개 부족
        Map<String, Object> row = jdbc.queryForMap(
                "select on_hand - allocated as available, incoming_qty, incoming_date from inventory where part_id = 'P-MCU-G1'");
        int available = ((Number) row.get("available")).intValue();
        assertThat(available).isEqualTo(1500);
        assertThat(SAMPLE_QTY - available).isEqualTo(500);
        LocalDate incoming = ((java.sql.Date) row.get("incoming_date")).toLocalDate();
        assertThat(incoming).isEqualTo(LocalDate.parse("2027-01-20")).isAfter(SAMPLE_DUE);
    }

    @Test
    void onlyTheHighTemperatureChipIsShortAmongAppliedParts() {
        List<Map<String, Object>> shortages = jdbc.queryForList("""
                select i.part_id, %d - (i.on_hand - i.allocated) as shortage
                from inventory i
                where i.part_id in ('P-MTR-BL12','P-IMP-25','P-HSG-AL','P-CON-4P','P-MCU-G1','P-SEAL-9K')
                  and %d > (i.on_hand - i.allocated)""".formatted(SAMPLE_QTY, SAMPLE_QTY));
        assertThat(shortages).hasSize(1);
        assertThat(shortages.get(0).get("part_id")).isEqualTo("P-MCU-G1");
    }

    @Test
    void substituteChipCoversTheShortageAtHigherCost() {
        Map<String, Object> row = jdbc.queryForMap("""
                select s.validation_days,
                       alt.unit_cost_krw - orig.unit_cost_krw as cost_delta,
                       i.on_hand - i.allocated                as alt_available
                from part_substitutes s
                join parts orig on orig.part_id = s.part_id
                join parts alt  on alt.part_id  = s.substitute_part_id
                join inventory i on i.part_id   = s.substitute_part_id
                where s.part_id = 'P-MCU-G1'""");
        assertThat(((Number) row.get("validation_days")).intValue()).isEqualTo(14);
        assertThat(((Number) row.get("cost_delta")).intValue()).isEqualTo(300);
        assertThat(((Number) row.get("alt_available")).intValue()).isGreaterThanOrEqualTo(500);
    }

    @Test
    void mustHitChunksForTheDemoExist() {
        List<String> ids = jdbc.queryForList("select chunk_id from doc_chunks", String.class);
        assertThat(ids).contains("dsn-002", "dsn-003", "dsn-004", "dsn-005", "dsn-007", "pol-003", "pol-004", "alt-002");
    }
}
