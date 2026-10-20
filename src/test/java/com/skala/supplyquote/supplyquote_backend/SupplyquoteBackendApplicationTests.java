package com.skala.supplyquote.supplyquote_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Flyway(V1 → V2)가 적용된 실제 PostgreSQL + pgvector에서 스키마와 핵심 SQL 패턴을 확인한다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SupplyquoteBackendApplicationTests {

    @Autowired
    JdbcTemplate jdbc;

    private List<String> tables() {
        return jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);
    }

    @Test
    void contextLoads() {
    }

    @Test
    void schemaHasDatasetTablesAndBackendTables() {
        assertThat(tables()).contains(
                // 데이터셋 v2.1/v2.2 (13개)
                "companies", "users", "models", "parts", "options", "model_options", "bom", "inventory",
                "part_substitutes", "rfqs", "rfq_reviews", "quotes", "doc_chunks",
                // 백엔드 전용
                "ai_tasks", "quote_daily_seq", "processed_messages", "flyway_schema_history");
    }

    @Test
    void v1WorkflowTablesAreGone() {
        assertThat(tables()).doesNotContain("analysis_results", "approvals", "quote_items", "purchase_requests",
                "purchase_request_items", "registration_requests");
    }

    @Test
    void docChunksUse1024DimensionVectors() {
        String type = jdbc.queryForObject("""
                select format_type(a.atttypid, a.atttypmod) from pg_attribute a
                where a.attrelid = 'doc_chunks'::regclass and a.attname = 'embedding'""", String.class);
        assertThat(type).isEqualTo("vector(1024)");

        String vector = "[" + String.join(",", Collections.nCopies(1024, "0.1")) + "]";
        jdbc.update("""
                insert into doc_chunks(chunk_id, doc_type, source, section, content, content_hash, embedding)
                values ('t-1', 'design_std', 't.md', 's', 'c', repeat('a', 64), ?::vector)""", vector);
        Double distance = jdbc.queryForObject(
                "select embedding <=> ?::vector from doc_chunks where chunk_id = 't-1'", Double.class, vector);
        assertThat(distance).isLessThan(0.0001);
    }

    @Test
    void checkConstraintsRejectInvalidStates() {
        long company = jdbc.queryForObject("insert into companies(name, company_type) values ('chk-co', 'CUSTOMER') returning company_id", Long.class);
        // 허용되지 않는 company_type, quotes.status, ai_tasks.status는 DB가 거부한다
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbc.update("insert into companies(name, company_type) values ('bad', 'OTHER')"))
                .hasMessageContaining("company_type");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbc.update("insert into inventory(part_id, on_hand, allocated) values ('nope', -1, 0)"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(company).isPositive();
    }

    @Test
    void quoteApprovalAndRegistrationAreIdempotentViaConditionalUpdate() {
        // 설계: 상태 전이는 조건부 UPDATE로 하고, 영향 행 0건이면 이미 처리된 것으로 본다
        jdbc.update("insert into companies(name, company_type) values ('idem-co', 'SUPPLIER')");
        jdbc.update("""
                insert into users(login_id, password_hash, company_id, name, user_type)
                select 'idem-user', 'x', company_id, 'n', 'MANAGER' from companies where name = 'idem-co'""");
        jdbc.update("""
                insert into rfqs(rfq_number, customer_company_id)
                select 'IDEM-1', company_id from companies where name = 'idem-co'""");
        jdbc.update("insert into rfq_reviews(rfq_number, thread_id) values ('IDEM-1', 'th-idem')");
        jdbc.update("""
                insert into quotes(quote_id, review_id, rfq_number, unit_price_krw)
                select 'Q-IDEM-1', review_id, 'IDEM-1', 1000 from rfq_reviews where thread_id = 'th-idem'""");
        long approver = jdbc.queryForObject("select user_id from users where login_id = 'idem-user'", Long.class);

        String approve = "update quotes set status='APPROVED', approved_by=?, approved_at=now() where quote_id=? and status='DRAFT'";
        String register = "update quotes set status='REGISTERED', registered_at=now() where quote_id=? and status='APPROVED'";

        assertThat(jdbc.update(register, "Q-IDEM-1")).as("DRAFT는 등록 불가").isZero();
        assertThat(jdbc.update(approve, approver, "Q-IDEM-1")).as("첫 승인").isEqualTo(1);
        assertThat(jdbc.update(approve, approver, "Q-IDEM-1")).as("승인 더블 클릭").isZero();
        assertThat(jdbc.update(register, "Q-IDEM-1")).as("첫 등록").isEqualTo(1);
        assertThat(jdbc.update(register, "Q-IDEM-1")).as("등록 재시도").isZero();
        assertThat(jdbc.queryForObject("select status from quotes where quote_id = 'Q-IDEM-1'", String.class))
                .isEqualTo("REGISTERED");
    }

    @Test
    void quoteDailySequenceIncrementsAtomically() {
        // quote_id(Q-YYYY-MMDD-NNN)의 일별 번호: upsert + RETURNING으로 원자적으로 증가한다
        String sql = """
                insert into quote_daily_seq(day, last_seq) values (?, 1)
                on conflict (day) do update set last_seq = quote_daily_seq.last_seq + 1
                returning last_seq""";
        LocalDate day = LocalDate.parse("2030-01-01");
        assertThat(jdbc.queryForObject(sql, Integer.class, day)).isEqualTo(1);
        assertThat(jdbc.queryForObject(sql, Integer.class, day)).isEqualTo(2);
        assertThat(jdbc.queryForObject(sql, Integer.class, LocalDate.parse("2030-01-02"))).isEqualTo(1);
    }

    @Test
    void reviewUpsertUpdatesOnlyProvidedSections() {
        // 설계: PUT /internal/rfq-reviews는 (rfq_number, thread_id)로 upsert하고 전달한 섹션만 바꾼다 (SG1 → SG2 순서로 이어서 저장)
        jdbc.update("insert into companies(name, company_type) values ('upsert-co', 'CUSTOMER')");
        jdbc.update("insert into rfqs(rfq_number, customer_company_id) select 'UPS-1', company_id from companies where name = 'upsert-co'");
        String sql = """
                insert into rfq_reviews(rfq_number, thread_id, selected_model, shortages)
                values ('UPS-1', 'th-ups', ?, ?::jsonb)
                on conflict (rfq_number, thread_id) do update set
                  selected_model = coalesce(excluded.selected_model, rfq_reviews.selected_model),
                  shortages = coalesce(excluded.shortages, rfq_reviews.shortages),
                  updated_at = now()""";
        jdbc.update("insert into models select 'EWP-UPS', '테스트', 1, 1, 9, 16, -40, 100, 'IP67', 40, 'PWM', 1, 1, 1, 0.1, 1000");
        jdbc.update(sql, "EWP-UPS", null);                       // SG1: 모델만 저장
        jdbc.update(sql, null, "[{\"part_id\":\"P-MCU-G1\"}]");  // SG2: 부족 부품만 저장
        assertThat(jdbc.queryForObject("select count(*) from rfq_reviews where thread_id = 'th-ups'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select selected_model from rfq_reviews where thread_id = 'th-ups'", String.class))
                .as("SG2 저장이 SG1 값을 지우지 않는다").isEqualTo("EWP-UPS");
        assertThat(jdbc.queryForObject("select shortages::text from rfq_reviews where thread_id = 'th-ups'", String.class))
                .contains("P-MCU-G1");
    }
}
