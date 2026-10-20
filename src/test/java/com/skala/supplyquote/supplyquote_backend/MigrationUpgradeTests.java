package com.skala.supplyquote.supplyquote_backend;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V1이 이미 푸시되어 팀원 DB에 적용돼 있을 수 있으므로, **V1 데이터가 들어 있는 DB가 V2로 올라가는지** 확인한다.
 * V1 시점의 데모 데이터와 업무 데이터를 넣은 뒤 V2를 적용한다.
 */
@Testcontainers
class MigrationUpgradeTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    private Flyway flyway(DataSource ds, String target) {
        return Flyway.configure().dataSource(ds).locations("classpath:db/migration").target(target).load();
    }

    @Test
    void v1DatabaseWithDataUpgradesToV2() {
        DataSource ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        // 1) V1 적용 + V1 시점 데이터 (이전 데이터셋 시드, 업무 테이블 행, 소비자 멱등 기록)
        flyway(ds, "1").migrate();
        new ResourceDatabasePopulator(new ClassPathResource("migration/v1_demo_data.sql")).execute(ds);
        jdbc.update("insert into rfqs(customer_id, title, content) select id, 't', 'c' from users limit 1");
        jdbc.update("insert into ai_tasks(rfq_id, requested_by, thread_id, request_id) select r.id, r.customer_id, 'th-1', gen_random_uuid() from rfqs r");
        jdbc.update("insert into analysis_results(task_id, rfq_id, version) select t.id, t.rfq_id, 1 from ai_tasks t");
        jdbc.update("insert into approvals(analysis_result_id, approver_id, decision) select a.id, 1, 'APPROVED' from analysis_results a");
        jdbc.update("insert into quotes(rfq_id, analysis_result_id, total_price) select a.rfq_id, a.id, 1000 from analysis_results a");
        jdbc.update("insert into processed_messages(message_id, consumer) values (gen_random_uuid(), 'keep-me')");
        assertThat(jdbc.queryForObject("select count(*) from models", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from doc_chunks", Integer.class)).isEqualTo(22);

        // 2) V2 적용
        var result = flyway(ds, "latest").migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(1);

        // 3) V1의 데모·업무 데이터는 사라지고, 소비자 멱등 기록은 유지된다
        assertThat(jdbc.queryForObject("select count(*) from models", Integer.class)).as("카탈로그 데모 데이터 삭제").isZero();
        assertThat(jdbc.queryForObject("select count(*) from doc_chunks", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from processed_messages where consumer = 'keep-me'", Integer.class))
                .as("processed_messages는 유지").isEqualTo(1);

        // 4) 새 구조
        assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_name = 'users'", String.class))
                .contains("login_id", "company_id", "user_type").doesNotContain("email");
        assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_name = 'part_substitutes'", String.class))
                .contains("pin_compatible", "ref_chunk_id").doesNotContain("compatibility_note", "requires_spec_revalidation");
        String vectorType = jdbc.queryForObject("""
                select format_type(a.atttypid, a.atttypmod) from pg_attribute a
                where a.attrelid = 'doc_chunks'::regclass and a.attname = 'embedding'""", String.class);
        assertThat(vectorType).isEqualTo("vector(1024)");

        // 5) 데이터셋 시드가 올라가는 구조인지 (V2 직후 카탈로그가 비어 있으므로 FK 충돌이 없다)
        new ResourceDatabasePopulator(
                new ClassPathResource("db/seed/02_seed.sql"), new ClassPathResource("db/seed/03_doc_chunks.sql")).execute(ds);
        assertThat(jdbc.queryForObject("select count(*) from doc_chunks", Integer.class)).isEqualTo(26);
        assertThat(jdbc.queryForObject("select count(*) from rfqs", Integer.class)).isEqualTo(1);
    }
}
