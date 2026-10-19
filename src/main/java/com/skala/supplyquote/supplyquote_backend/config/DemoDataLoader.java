package com.skala.supplyquote.supplyquote_backend.config;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * demo 프로파일에서 데모 데이터(db/seed/demo_data.sql)를 적재한다.
 * Flyway 마이그레이션이 아니라서 운영·테스트 DB에는 들어가지 않고, models 테이블이 비어 있을 때만 한 번 적재한다.
 * 실행: SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
 */
@Component
@Profile("demo")
class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    DemoDataLoader(DataSource dataSource, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject("select count(*) from models", Integer.class);
        if (existing != null && existing > 0) {
            log.info("데모 데이터가 이미 있어 건너뜁니다 (models {}건).", existing);
            return;
        }
        var populator = new ResourceDatabasePopulator(new ClassPathResource("db/seed/demo_data.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        // 한 트랜잭션으로 적재해서 중간에 실패하면 전부 되돌린다
        tx.executeWithoutResult(status -> populator.populate(DataSourceUtils.getConnection(dataSource)));
        log.info("데모 데이터를 적재했습니다 (RFQ HB-EV7-EWP-001 시나리오).");
    }
}
