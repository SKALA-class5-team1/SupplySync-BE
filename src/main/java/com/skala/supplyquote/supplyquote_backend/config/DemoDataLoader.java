package com.skala.supplyquote.supplyquote_backend.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * demo 프로파일에서 데이터셋 시드(db/seed/*.sql)를 적재한다.
 * 시드 파일은 개인폴더/Input/rfq_dataset_v2.2의 파일을 수정 없이 복사한 것이다. 데이터셋이 바뀌면 파일만 교체한다.
 *
 * <ul>
 *   <li>Flyway 마이그레이션이 아니라서 운영·테스트 DB에는 들어가지 않는다.</li>
 *   <li>models 테이블이 비어 있을 때만 SQL을 적재한다 (재기동해도 중복되지 않는다).</li>
 *   <li>시드의 rfqs.file_path가 가리키는 RFQ 원문 파일을 rfq-files-dir에 배치한다 (이미 있으면 덮어쓴다).</li>
 * </ul>
 *
 * 실행: SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
 */
@Component
@Profile("demo")
class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    /** 적재 순서가 중요하다 (02_seed가 companies, parts 등을 먼저 만든다). */
    private static final List<String> SQL_FILES = List.of("db/seed/02_seed.sql", "db/seed/03_doc_chunks.sql");

    /** rfqs.file_path 값(rfq-files-dir 기준 상대 경로)과 같은 구조로 classpath db/seed 아래에 있다. */
    private static final List<String> RFQ_FILES = List.of("01_rfq/RFQ_HB-EV7-EWP-001.md");

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Path rfqFilesDir;

    DemoDataLoader(DataSource dataSource, JdbcTemplate jdbc, TransactionTemplate tx,
                   @Value("${app.rfq-files-dir:./rfq-files}") String rfqFilesDir) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.tx = tx;
        this.rfqFilesDir = Path.of(rfqFilesDir);
    }

    @Override
    public void run(ApplicationArguments args) {
        copyRfqFiles();

        Integer existing = jdbc.queryForObject("select count(*) from models", Integer.class);
        if (existing != null && existing > 0) {
            log.info("데모 데이터가 이미 있어 건너뜁니다 (models {}건).", existing);
            return;
        }
        Resource[] scripts = SQL_FILES.stream().map(ClassPathResource::new).toArray(Resource[]::new);
        var populator = new ResourceDatabasePopulator(scripts);
        populator.setSqlScriptEncoding("UTF-8");
        // 한 트랜잭션으로 적재해서 중간에 실패하면 전부 되돌린다
        tx.executeWithoutResult(status -> populator.populate(DataSourceUtils.getConnection(dataSource)));
        log.info("데모 데이터를 적재했습니다 (데이터셋 v2.2: RFQ HB-EV7-EWP-001 시나리오).");
    }

    private void copyRfqFiles() {
        for (String relative : RFQ_FILES) {
            Path target = rfqFilesDir.resolve(relative);
            try (InputStream in = new ClassPathResource("db/seed/" + relative).getInputStream()) {
                Files.createDirectories(target.getParent());
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("RFQ 원문 파일을 배치하지 못했습니다: " + target, e);
            }
        }
        log.info("RFQ 원문 파일을 배치했습니다: {}", rfqFilesDir.toAbsolutePath().normalize());
    }
}
