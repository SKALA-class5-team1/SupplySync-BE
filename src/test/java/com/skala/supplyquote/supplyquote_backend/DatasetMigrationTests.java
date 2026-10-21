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

/** Supabase처럼 vector 확장을 extensions 스키마에 설치한 빈 DB에서 v2와 후속 마이그레이션을 검증한다. */
@Testcontainers
class DatasetMigrationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Test
    void datasetAndQuoteDetailsMigrationsLoadOnSupabaseStyleDatabase() {
        DataSource admin = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate adminJdbc = new JdbcTemplate(admin);
        adminJdbc.execute("create schema extensions");
        adminJdbc.execute("create extension vector with schema extensions");
        adminJdbc.execute("create table public.supabase_default_fixture(id int)");

        DataSource ds = new DriverManagerDataSource(postgres.getJdbcUrl() + "?currentSchema=public,extensions",
                postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        var v2 = Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                .baselineOnMigrate(true).baselineVersion("0").target("2").load().migrate();
        assertThat(v2.success).isTrue();
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/02_seed.sql"),
                new ClassPathResource("db/seed/03_doc_chunks.sql")).execute(ds);
        jdbc.update("insert into rfq_reviews(rfq_number,thread_id) values ('HB-EV7-EWP-001','legacy-thread')");
        jdbc.update("""
                insert into quotes(quote_id,review_id,rfq_number,unit_price_krw,created_at)
                select 'Q-2026-1019-009',review_id,'HB-EV7-EWP-001',1000,'2026-10-20 10:00:00'::timestamp
                from rfq_reviews where thread_id='legacy-thread'
                """);
        var result = Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                .baselineOnMigrate(true).baselineVersion("0").load().migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(5);
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank",
                String.class)).containsExactly("0", "2", "3", "4", "5", "6", "7");
        assertThat(jdbc.queryForObject("select revision from quotes where quote_id='Q-2026-1019-009'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForList("""
                select table_name || '.' || column_name from information_schema.columns
                where table_schema='public' and
                ((table_name='rfq_reviews' and column_name='final_plan') or
                 (table_name='quotes' and column_name in ('issue_date','details')))
                order by table_name,column_name
                """, String.class)).containsExactly("quotes.details", "quotes.issue_date", "rfq_reviews.final_plan");
        assertThat(jdbc.queryForObject("select issue_date from quotes where quote_id='Q-2026-1019-009'",
                java.time.LocalDate.class)).isEqualTo(java.time.LocalDate.parse("2026-10-19"));
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' "
                + "and table_name in ('companies','users','models','parts','options','model_options','bom',"
                + "'inventory','part_substitutes','rfqs','rfq_reviews','quotes','doc_chunks',"
                + "'ai_tasks','quote_daily_seq','processed_messages','analysis_outbox')", Integer.class)).isEqualTo(17);
        assertThat(jdbc.queryForObject("select format_type(a.atttypid, a.atttypmod) from pg_attribute a "
                + "where a.attrelid='doc_chunks'::regclass and a.attname='embedding'", String.class))
                .endsWith("vector(1024)");

        assertThat(jdbc.queryForObject("select count(*) from models", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from doc_chunks", Integer.class)).isEqualTo(26);
        assertThat(jdbc.queryForObject("select count(*) from rfqs", Integer.class)).isEqualTo(1);
    }
}
