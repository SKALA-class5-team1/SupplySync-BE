package com.skala.supplyquote.supplyquote_backend.service;

import com.skala.supplyquote.supplyquote_backend.domain.EventType;
import com.skala.supplyquote.supplyquote_backend.internal.dto.EventDto;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agent가 진행 이벤트를 더 보내지 않는 작업을 FAILED로 바꾼다. 사람 입력을 기다리는 상태(NEEDS_CLARIFICATION,
 * WAITING_SAVE_APPROVAL, WAITING_APPROVAL)는 길어질 수 있어 대상에서 뺀다. 기준은 마지막 updated_at이다.
 */
@Component
@EnableScheduling
public class StalledTaskWatchdog {
    private static final Logger log = LoggerFactory.getLogger(StalledTaskWatchdog.class);

    private final JdbcTemplate jdbc;
    private final TaskEventStream stream;
    private final boolean enabled;
    private final int queuedMinutes;
    private final int activeMinutes;

    public StalledTaskWatchdog(JdbcTemplate jdbc, TaskEventStream stream,
                               @Value("${app.watchdog.enabled:true}") boolean enabled,
                               @Value("${app.watchdog.queued-minutes:10}") int queuedMinutes,
                               @Value("${app.watchdog.active-minutes:30}") int activeMinutes) {
        this.jdbc = jdbc;
        this.stream = stream;
        this.enabled = enabled;
        this.queuedMinutes = queuedMinutes;
        this.activeMinutes = activeMinutes;
    }

    @Scheduled(fixedDelayString = "${app.watchdog.interval-ms:60000}")
    public void sweep() {
        if (!enabled) return;
        fail("status = 'QUEUED'", queuedMinutes, "분석이 시작되지 않았습니다 (" + queuedMinutes + "분 초과)");
        fail("status in ('RUNNING','REVALIDATING','REGISTERING')", activeMinutes,
                "Agent 진행 이벤트가 " + activeMinutes + "분 이상 없어 중단 처리했습니다");
    }

    private void fail(String statusCondition, int minutes, String reason) {
        var ids = jdbc.query("""
                update ai_tasks set status='FAILED', failure_reason=?, completed_at=now(), updated_at=now()
                where %s and updated_at < now() - make_interval(mins => ?)
                returning task_id
                """.formatted(statusCondition), (rs, row) -> (UUID) rs.getObject(1), reason, minutes);
        for (UUID id : ids) {
            log.warn("멈춘 작업을 FAILED로 변경: taskId={}, reason={}", id, reason);
            stream.publish(id, new EventDto.EventRequest(EventType.RUN_FAILED, null, reason, null, null));
        }
    }
}
