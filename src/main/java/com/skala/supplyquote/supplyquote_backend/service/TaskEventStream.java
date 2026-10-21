package com.skala.supplyquote.supplyquote_backend.service;

import com.skala.supplyquote.supplyquote_backend.domain.EventType;
import com.skala.supplyquote.supplyquote_backend.internal.dto.EventDto;
import com.skala.supplyquote.supplyquote_backend.domain.UserType;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class TaskEventStream {
    private final Map<UUID, CopyOnWriteArrayList<Subscriber>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID taskId, UserType role, long timeoutMillis) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        var list = subscribers.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>());
        var subscriber = new Subscriber(emitter, role);
        list.add(subscriber);
        emitter.onCompletion(() -> remove(taskId, subscriber));
        emitter.onTimeout(() -> remove(taskId, subscriber));
        emitter.onError(error -> remove(taskId, subscriber));
        return emitter;
    }

    public void publish(UUID taskId, EventDto.EventRequest event) {
        var list = subscribers.get(taskId);
        if (list == null) return;
        var managerData = new HashMap<String, Object>(Map.of("task_id", taskId,
                "step", event.step() == null ? "" : event.step().name(),
                "message", event.message() == null ? "" : event.message(), "at", Instant.now().toString()));
        var customerData = new HashMap<String, Object>(Map.of("task_id", taskId,
                "step", event.step() == null ? "" : event.step().name(), "at", Instant.now().toString()));
        if (event.type() == EventType.RUN_END && event.data() != null) {
            managerData.put("outcome", event.data().get("outcome"));
            customerData.put("outcome", event.data().get("outcome"));
        }
        for (Subscriber subscriber : list) {
            if (subscriber.role() == UserType.CUSTOMER && event.type() != EventType.STEP_START
                    && event.type() != EventType.STEP_END && event.type() != EventType.DONE
                    && event.type() != EventType.FAILED && event.type() != EventType.RUN_END
                    && event.type() != EventType.RUN_FAILED) continue;
            SseEmitter emitter = subscriber.emitter();
            try {
                emitter.send(SseEmitter.event().name(event.type().name())
                        .data(subscriber.role() == UserType.MANAGER ? managerData : customerData));
                if (event.type() == EventType.DONE || event.type() == EventType.FAILED
                        || event.type() == EventType.RUN_END || event.type() == EventType.RUN_FAILED) emitter.complete();
            } catch (IOException | IllegalStateException e) {
                emitter.complete();
                remove(taskId, subscriber);
            }
        }
    }

    private void remove(UUID taskId, Subscriber subscriber) {
        var list = subscribers.get(taskId);
        if (list != null) {
            list.remove(subscriber);
            if (list.isEmpty()) subscribers.remove(taskId, list);
        }
    }

    private record Subscriber(SseEmitter emitter, UserType role) { }
}
