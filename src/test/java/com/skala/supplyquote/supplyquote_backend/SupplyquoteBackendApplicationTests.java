package com.skala.supplyquote.supplyquote_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SupplyquoteBackendApplicationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextLoads() {
    }

    @Test
    void flywayCreatesCoreTables() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(tables).contains(
                // 데이터셋(지영님) 테이블
                "models", "options", "model_options", "parts", "bom", "inventory", "rfq_reviews", "doc_chunks",
                // 대체 부품
                "part_substitutes",
                // 업무 흐름
                "users", "rfqs", "ai_tasks", "analysis_results", "approvals",
                "quotes", "quote_items", "purchase_requests", "purchase_request_items",
                "registration_requests", "processed_messages", "flyway_schema_history");
    }

    @Test
    void pgvectorIsInstalledAndDocChunkEmbeddingAcceptsVectors() {
        Integer ext = jdbc.queryForObject("select count(*) from pg_extension where extname = 'vector'", Integer.class);
        assertThat(ext).isEqualTo(1);
        // 1536차원 벡터를 저장하고 코사인 거리로 조회할 수 있다
        String vector = "[" + String.join(",", java.util.Collections.nCopies(1536, "0.1")) + "]";
        jdbc.update("insert into doc_chunks(chunk_id, doc_type, source, section, content, embedding) "
                + "values ('t-1','design_std','t.md','s','c', ?::vector)", vector);
        Double distance = jdbc.queryForObject(
                "select embedding <=> ?::vector from doc_chunks where chunk_id = 't-1'", Double.class, vector);
        assertThat(distance).isLessThan(0.0001);
    }

    @Test
    void registrationStepIsIdempotentPerRequestAndStep() {
        // (request_id, step) 복합 PK가 같은 단계의 중복 기록을 막는지 확인한다
        jdbc.update("insert into users(email, password_hash, name, role) values ('t@t.com','x','t','CUSTOMER')");
        jdbc.update("insert into rfqs(customer_id, title, content) select id,'t','c' from users");
        jdbc.update("""
                insert into ai_tasks(rfq_id, requested_by, thread_id, request_id)
                select r.id, r.customer_id, 'th-1', gen_random_uuid() from rfqs r""");
        jdbc.update("""
                insert into analysis_results(task_id, rfq_id, version)
                select t.id, t.rfq_id, 1 from ai_tasks t""");
        String request = "11111111-1111-1111-1111-111111111111";
        String insert = """
                insert into registration_requests(request_id, step, status, analysis_result_id)
                select ?::uuid, ?, 'SUCCEEDED', id from analysis_results""";
        jdbc.update(insert, request, "QUOTE");
        jdbc.update(insert, request, "PURCHASE_REQUEST");
        assertThatThrownBy(() -> jdbc.update(insert, request, "QUOTE")).isInstanceOf(DuplicateKeyException.class);
    }
}
