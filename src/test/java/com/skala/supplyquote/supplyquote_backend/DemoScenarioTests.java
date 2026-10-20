package com.skala.supplyquote.supplyquote_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 데이터셋 v2.2 시드를 적재한 DB가 **정답지(05_expected)와 같은 값**을 내는지 확인한다.
 * DB 값과 정답지 JSON을 직접 대조하므로 시드나 스키마가 바뀌어 데모 시나리오가 깨지면 여기서 먼저 알 수 있다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = "app.rfq-files-dir=build/test-rfq-files")
class DemoScenarioTests {

    private static final Set<String> OPTIONS = Set.of("OPT-HT", "OPT-IP9K");

    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    JdbcTemplate jdbc;

    private JsonNode expected(String file) throws IOException {
        try (InputStream in = new ClassPathResource("stub/" + file).getInputStream()) {
            return json.readTree(in);
        }
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return n == null ? 0 : n;
    }

    /** EWP-12-B에 옵션 두 개를 적용한 BOM의 부품 ID와 수량. 옵션이 대신 넣은 부품은 replaces 부품을 뺀다. */
    private List<Map<String, Object>> appliedBom() {
        return jdbc.queryForList("""
                select part_id, qty_per_unit from (
                    select part_id, qty_per_unit from bom
                     where model_id = 'EWP-12-B'
                       and part_id not in (select replaces_part_id from options where option_code in ('OPT-HT','OPT-IP9K'))
                    union all
                    select uses_part_id, 1 from options where option_code in ('OPT-HT','OPT-IP9K')
                ) t order by part_id""");
    }

    // ------------------------------------------------------------------ 적재 상태
    @Test
    void seedHasExpectedRowCounts() {
        assertThat(count("companies")).isEqualTo(2);
        assertThat(count("users")).isEqualTo(2);
        assertThat(count("models")).isEqualTo(3);
        assertThat(count("options")).isEqualTo(2);
        assertThat(count("model_options")).isEqualTo(3);
        assertThat(count("parts")).isEqualTo(9);
        assertThat(count("bom")).isEqualTo(12);
        assertThat(count("inventory")).isEqualTo(9);
        assertThat(count("part_substitutes")).isEqualTo(1);
        assertThat(count("rfqs")).isEqualTo(1);
        assertThat(count("doc_chunks")).isEqualTo(26);
        // Agent 실행 전이라 결과 테이블은 비어 있다
        assertThat(count("rfq_reviews")).isZero();
        assertThat(count("quotes")).isZero();
    }

    @Test
    void demoAccountsFollowTheCompanyRoleRule() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select u.login_id, u.user_type, c.name, c.company_type
                from users u join companies c using (company_id) order by u.user_id""");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("login_id", "hb_buyer01").containsEntry("user_type", "CUSTOMER")
                .containsEntry("name", "한별모터스").containsEntry("company_type", "CUSTOMER");
        assertThat(rows.get(1)).containsEntry("login_id", "ds_sales01").containsEntry("user_type", "MANAGER")
                .containsEntry("name", "대성오토텍").containsEntry("company_type", "SUPPLIER");
        // 규칙: CUSTOMER는 CUSTOMER 회사, MANAGER는 SUPPLIER 회사 소속
        for (var r : rows) {
            assertThat(r.get("user_type").equals("CUSTOMER")).isEqualTo(r.get("company_type").equals("CUSTOMER"));
        }
    }

    @Test
    void rfqStartsInReceivedStateWithoutExtractedValues() {
        Map<String, Object> rfq = jdbc.queryForMap("select * from rfqs");
        assertThat(rfq).containsEntry("rfq_number", "HB-EV7-EWP-001").containsEntry("status", "RECEIVED")
                .containsEntry("file_path", "01_rfq/RFQ_HB-EV7-EWP-001.md");
        assertThat(((Number) rfq.get("uploaded_by")).intValue()).isEqualTo(1);
        // 추출 컬럼은 SG1이 요청서를 읽은 뒤에 채운다
        assertThat(rfq.get("eau")).isNull();
        assertThat(rfq.get("sample_qty")).isNull();
        assertThat(rfq.get("sample_due_date")).isNull();
    }

    @Test
    void rfqFileIsPlacedWhereFilePathPoints() throws IOException {
        String filePath = jdbc.queryForObject("select file_path from rfqs", String.class);
        Path file = Path.of("build/test-rfq-files").resolve(filePath);
        assertThat(file).exists();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("HB-EV7-EWP-001").contains("22 L/h");
    }

    @Test
    void sequencesContinueAfterSeededIds() {
        // 시드가 setval로 시퀀스를 맞췄다: 새 행이 시드 ID와 충돌하지 않는다
        assertThat(jdbc.queryForObject("insert into companies(name, company_type) values ('seq-co', 'CUSTOMER') returning company_id", Long.class)).isEqualTo(3L);
    }

    // ------------------------------------------------------------------ 문서 청크
    @Test
    void chunkHashesMatchContentAndEmbeddingsArePending() throws NoSuchAlgorithmException {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        List<Map<String, Object>> chunks = jdbc.queryForList("select chunk_id, content, content_hash, embedding_status, embedding_model, embedded_at, (embedding is null) as no_vec from doc_chunks");
        assertThat(chunks).hasSize(26);
        for (var c : chunks) {
            String hash = HexFormat.of().formatHex(sha.digest(((String) c.get("content")).getBytes(StandardCharsets.UTF_8)));
            assertThat(c.get("content_hash")).as("%s content_hash", c.get("chunk_id")).isEqualTo(hash);
            assertThat(c.get("embedding_status")).isEqualTo("PENDING");
            assertThat(c.get("no_vec")).isEqualTo(true);
            assertThat(c.get("embedding_model")).isNull();
        }
    }

    @Test
    void mustHitChunksExist() {
        List<String> ids = jdbc.queryForList("select chunk_id from doc_chunks", String.class);
        assertThat(ids).contains("dsn-002", "dsn-003", "dsn-004", "dsn-005", "dsn-007",
                "pol-003", "pol-004", "pol-009", "pol-010", "alt-002", "alt-003");
        // 제16조(견적 마진), 제17조(견적서 형식), 제20·21조(대체 부품)가 영업 규정 청크로 들어 있다
        assertThat(jdbc.queryForObject("select count(*) from doc_chunks where doc_type = 'policy'", Integer.class)).isEqualTo(10);
    }

    // ------------------------------------------------------------------ SG1: 제품·옵션
    @Test
    void recommendedModelWithOptionsMatchesSpecMatch() throws IOException {
        JsonNode state = expected("4_output_state.json");
        Map<String, Object> row = jdbc.queryForMap("""
                select m.base_price_krw + sum(o.add_price_krw) as price,
                       m.weight_kg + sum(o.add_weight_kg)      as weight,
                       m.noise_db + sum(o.add_noise_db)        as noise
                from models m
                join model_options mo on mo.model_id = m.model_id
                join options o on o.option_code = mo.option_code
                where m.model_id = 'EWP-12-B' and o.option_code in ('OPT-HT', 'OPT-IP9K')
                group by m.base_price_krw, m.weight_kg, m.noise_db""");
        assertThat(((Number) row.get("price")).intValue()).isEqualTo(state.get("quoted_price_krw").asInt()).isEqualTo(37300);
        assertThat(((Number) row.get("price")).intValue()).isLessThanOrEqualTo(state.get("target_price_krw").asInt());
        assertThat(((Number) row.get("weight")).doubleValue()).isEqualTo(0.75);
        assertThat(((Number) row.get("noise")).intValue()).isEqualTo(43);
    }

    // ------------------------------------------------------------------ SG2: BOM·재고·대안 (DB와 정답지 대조)
    @Test
    void appliedBomMatchesBomEffective() throws IOException {
        Set<String> fromDb = new LinkedHashSet<>();
        appliedBom().forEach(r -> fromDb.add((String) r.get("part_id")));
        Set<String> fromExpected = new LinkedHashSet<>();
        expected("5_sg2_supply.json").get("bom_effective").forEach(n -> fromExpected.add(n.get("part_id").asString()));
        assertThat(fromDb).containsExactlyInAnyOrderElementsOf(fromExpected).hasSize(6);
        assertThat(fromDb).contains("P-MCU-G1", "P-SEAL-9K").doesNotContain("P-MCU-STD", "P-SEAL-STD");
    }

    @Test
    void supplyCheckMatchesExpected() throws IOException {
        JsonNode sg2 = expected("5_sg2_supply.json");
        int sampleQty = sg2.get("demand").get("sample_qty").asInt();
        Map<String, Integer> qty = new java.util.HashMap<>();
        appliedBom().forEach(r -> qty.put((String) r.get("part_id"), ((Number) r.get("qty_per_unit")).intValue()));

        for (JsonNode e : sg2.get("supply_check")) {
            String part = e.get("part_id").asString();
            Map<String, Object> inv = jdbc.queryForMap(
                    "select on_hand, allocated, incoming_qty, incoming_date from inventory where part_id = ?", part);
            int onHand = ((Number) inv.get("on_hand")).intValue();
            int allocated = ((Number) inv.get("allocated")).intValue();
            int required = qty.get(part) * sampleQty;
            int available = onHand - allocated;
            assertThat(e.get("on_hand").asInt()).as("%s on_hand", part).isEqualTo(onHand);
            assertThat(e.get("allocated").asInt()).as("%s allocated", part).isEqualTo(allocated);
            assertThat(e.get("available").asInt()).as("%s available", part).isEqualTo(available);
            assertThat(e.get("required_qty").asInt()).as("%s required", part).isEqualTo(required);
            assertThat(e.get("short_qty").asInt()).as("%s short", part).isEqualTo(Math.max(0, required - available));
            assertThat(e.get("incoming_qty").asInt()).as("%s incoming_qty", part).isEqualTo(((Number) inv.get("incoming_qty")).intValue());
            Object date = inv.get("incoming_date");
            assertThat(e.get("incoming_date").isNull() ? null : e.get("incoming_date").asString())
                    .as("%s incoming_date", part).isEqualTo(date == null ? null : date.toString());
        }
    }

    @Test
    void shortageAndDelayMatchExpected() throws IOException {
        JsonNode sg2 = expected("5_sg2_supply.json");
        LocalDate due = LocalDate.parse(sg2.get("demand").get("sample_due_date").asString());
        JsonNode s = sg2.get("shortages").get(0);
        Map<String, Object> part = jdbc.queryForMap("select lead_time_days, is_critical from parts where part_id = 'P-MCU-G1'");
        LocalDate incoming = ((java.sql.Date) jdbc.queryForMap("select incoming_date from inventory where part_id = 'P-MCU-G1'").get("incoming_date")).toLocalDate();
        assertThat(s.get("short_qty").asInt()).isEqualTo(500);
        assertThat(s.get("lead_time_days").asInt()).isEqualTo(((Number) part.get("lead_time_days")).intValue());
        assertThat(s.get("is_critical").asBoolean()).isEqualTo(part.get("is_critical"));
        assertThat(s.get("incoming_delay_days").asInt()).isEqualTo((int) ChronoUnit.DAYS.between(due, incoming)).isEqualTo(36);
    }

    @Test
    void substituteMatchesSubstitutePartAlternative() throws IOException {
        JsonNode alt = null;
        for (JsonNode a : expected("5_sg2_supply.json").get("alternatives")) {
            if ("SUBSTITUTE_PART".equals(a.get("type").asString())) {
                alt = a;
            }
        }
        assertThat(alt).isNotNull();
        Map<String, Object> sub = jdbc.queryForMap("""
                select s.substitute_part_id, s.validation_days, s.pin_compatible, s.ref_chunk_id,
                       alt.unit_cost_krw - orig.unit_cost_krw as cost_diff, alt.supplier,
                       i.on_hand - i.allocated as alt_available
                from part_substitutes s
                join parts orig on orig.part_id = s.part_id
                join parts alt  on alt.part_id  = s.substitute_part_id
                join inventory i on i.part_id   = s.substitute_part_id
                where s.part_id = 'P-MCU-G1'""");
        assertThat(sub.get("substitute_part_id")).isEqualTo(alt.get("substitute_part_id").asString());
        assertThat(((Number) sub.get("validation_days")).intValue()).isEqualTo(alt.get("validation_days").asInt()).isEqualTo(14);
        assertThat(((Number) sub.get("cost_diff")).intValue()).isEqualTo(alt.get("unit_cost_diff_krw").asInt()).isEqualTo(300);
        assertThat(sub.get("supplier")).isEqualTo(alt.get("substitute_supplier").asString());
        assertThat(sub.get("pin_compatible")).isEqualTo(true);
        assertThat(sub.get("ref_chunk_id")).isEqualTo("alt-002");
        // 대체 부품 가용 재고가 부족 수량을 충당한다
        assertThat(((Number) sub.get("alt_available")).intValue()).isGreaterThanOrEqualTo(alt.get("covered_qty").asInt());
        // 근거 청크가 실제로 존재한다
        for (JsonNode ref : alt.get("refs")) {
            assertThat(jdbc.queryForObject("select count(*) from doc_chunks where chunk_id = ?", Integer.class, ref.asString()))
                    .as("근거 청크 %s", ref.asString()).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ SG3: 견적 원가
    @Test
    void partsCostMatchesQuoteDraft() throws IOException {
        JsonNode q = expected("6_sg3_quote.json").get("quote_draft");
        Integer cost = jdbc.queryForObject("""
                select sum(p.unit_cost_krw * a.qty_per_unit) from (
                    select part_id, qty_per_unit from bom
                     where model_id = 'EWP-12-B'
                       and part_id not in (select replaces_part_id from options where option_code in ('OPT-HT','OPT-IP9K'))
                    union all
                    select uses_part_id, 1 from options where option_code in ('OPT-HT','OPT-IP9K')
                ) a join parts p using (part_id)""", Integer.class);
        assertThat(cost).isEqualTo(q.get("parts_cost_krw").asInt()).isEqualTo(21650);
        // 원가 구성의 각 부품 단가가 parts와 같다
        for (JsonNode c : q.get("parts_cost_breakdown")) {
            assertThat(jdbc.queryForObject("select unit_cost_krw from parts where part_id = ?", Integer.class, c.get("part_id").asString()))
                    .isEqualTo(c.get("cost_krw").asInt());
        }
        // 단가 구성: 모델 기본가와 옵션 추가가
        for (JsonNode p : q.get("price_breakdown")) {
            String code = p.get("code").asString();
            String sql = code.startsWith("EWP") ? "select base_price_krw from models where model_id = ?" : "select add_price_krw from options where option_code = ?";
            assertThat(jdbc.queryForObject(sql, Integer.class, code)).as(code).isEqualTo(p.get("amount_krw").asInt());
        }
    }
}
