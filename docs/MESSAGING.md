# RabbitMQ 분석 요청·재개

Spring이 생산자와 소비자다. 큐는 Agent **실행·재개 명령**만 전달한다. Agent의 결과 저장, 진행 이벤트와 SSE는 기존 HTTP 경로를 사용한다. RAG 검색과 새 문서 벡터 적재도 이 큐를 사용하지 않는다.

## 활성화

`ANALYSIS_MQ_ENABLED=false`가 기본값이다. Agent core의 실행·재개 API가 준비되고 `AGENT_BASE_URL`, RabbitMQ 접속 정보가 설정된 뒤 `true`로 전환한다. 꺼져 있으면 기존 Agent HTTP 어댑터를 사용한다. 실제 Agent core와의 통합 검증은 아직 남아 있다.

## 경로

```text
RFQ 업로드 / 요구 확인 / 견적 결정
  → 업무 DB와 analysis_outbox를 한 트랜잭션에 저장
  → outbox 발행기(5초 간격, publisher confirm)
  → supplysync.analysis exchange
  → analysis.request.q
  → Spring 소비자(중복 검사, Agent HTTP 호출)
  → Agent → Spring /internal/** (결과·이벤트)

소비 실패: 최대 3번 추가 재시도(1·2·4초) → analysis.request.dlq
```

| 자원 | 설정 |
| --- | --- |
| `supplysync.analysis` | durable topic exchange |
| `analysis.request.q` | durable queue; `analysis.requested`, `analysis.resume` 바인딩 |
| `supplysync.analysis.dlx` | durable direct exchange |
| `analysis.request.dlq` | 소비 최종 실패 메시지 |

`analysis.completed`, `analysis.failed`, `document.embed.requested`는 현재 사용하지 않는 이전 설계 상수다. 결과는 `PUT /internal/rfq-reviews`, `POST /internal/quotes`, `POST /internal/tasks/{taskId}/events`로 받는다.

## 메시지

JSON 필드명은 Spring 설정에 따라 snake_case다. 봉투에는 `message_id`(전달 중복 키), `request_id`(업무 요청 추적 키), `type`, `schema_version=1`, `occurred_at`, `payload`가 들어간다. AMQP 속성에는 영속 메시지, `message_id`, `correlation_id=request_id`, 동일한 식별 헤더를 사용한다. RFQ 원문과 파일은 싣지 않는다.

| type | payload | 소비자 동작 |
| --- | --- | --- |
| `analysis.requested` | `task_id`, `rfq_id`, `requested_by`, `thread_id` | DB에서 `rfq_number`를 확인하고 Agent `/runs` 호출. 접수되면 QUEUED → RUNNING |
| `analysis.resume` | `task_id`, `thread_id`, `kind`, `user_id`, `decision` | 담당자 입력 당시의 결정으로 Agent `/runs/{threadId}/resume` 호출 |

`kind`는 `CLARIFICATION`, `APPROVED`, `REJECTED`, `REVISE`다. `decision`에는 확인된 가정이나 승인 결정·사유가 들어간다. 원문 파일과 견적 전체를 싣지 않는다.

## 전달·중복·실패 처리

- 업무 데이터와 outbox 행은 **같은 DB 트랜잭션**으로 커밋한다. RabbitMQ가 잠시 중단돼도 outbox 행은 남는다.
- 발행기는 미발행 행을 `FOR UPDATE SKIP LOCKED`로 가져와 브로커 확인 응답과 라우팅 성공을 받은 뒤 `published_at`을 기록한다. 발행 실패는 `attempt_count`, `last_error`를 기록하고 다음 주기에 재시도한다.
- 소비자는 `processed_messages(message_id, consumer)`로 브로커 재전달을 걸러낸다. Agent HTTP가 성공한 뒤 처리 기록을 남긴다. 중간 장애 시 Agent가 같은 `request_id`의 재호출을 받을 수 있으므로 Agent core의 멱등 처리가 필요하다.
- Agent 호출은 최초 시도 뒤 1·2·4초 간격으로 세 번 더 시도한다. 계속 실패하면 작업을 FAILED로 기록하고 메시지를 재큐잉 없이 거절해 DLQ로 보낸다. DLQ 재처리는 원인 해결 및 작업 상태 재설정 후 수동으로 진행한다.
- 소비자는 두 개 동시 실행, prefetch 1로 설정했다. 메시지 간 순서는 보장하지 않는다.

## 남은 통합 검증

- Agent core의 실제 `/runs`, `/resume` 요청·응답, `request_id` 멱등 처리 확인.
- 로컬 RabbitMQ에서 outbox 발행, 소비, 실패·DLQ와 장애 복구를 실제로 검증.
- `ANALYSIS_MQ_ENABLED=true`를 켜기 전 Agent core 주소와 인증 키를 설정.
