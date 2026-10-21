package com.skala.supplyquote.supplyquote_backend.messaging;

import com.skala.supplyquote.supplyquote_backend.messaging.contract.MessagingContract;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 커밋된 outbox를 확인 응답을 받은 뒤에만 발행 완료로 표시한다. */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
public class AnalysisOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(AnalysisOutboxRelay.class);
    private final JdbcTemplate jdbc;
    private final AnalysisMessagePublisher publisher;

    public AnalysisOutboxRelay(JdbcTemplate jdbc, AnalysisMessagePublisher publisher) {
        this.jdbc = jdbc;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.messaging.relay-delay-ms:5000}")
    @Transactional
    public void relay() {
        var pending = jdbc.query("""
                select message_id,request_id,type,body::text from analysis_outbox
                where published_at is null order by created_at limit 10 for update skip locked
                """, (rs, row) -> new Pending((UUID) rs.getObject(1), (UUID) rs.getObject(2),
                rs.getString(3), rs.getString(4)));
        for (var item : pending) {
            try {
                publisher.publish(item.body(), item.messageId(), item.requestId(), item.type(),
                        MessagingContract.SCHEMA_VERSION);
                jdbc.update("update analysis_outbox set published_at=now(),last_error=null where message_id=?",
                        item.messageId());
            } catch (Exception e) {
                log.warn("분석 메시지 발행 재시도 예정: messageId={}", item.messageId(), e);
                jdbc.update("update analysis_outbox set attempt_count=attempt_count+1,last_error=? where message_id=?",
                        e.getMessage(), item.messageId());
            }
        }
    }

    private record Pending(UUID messageId, UUID requestId, String type, String body) { }
}
