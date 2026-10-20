# 메시지 규약 (RabbitMQ)

생산자와 소비자 모두 Spring Boot 백엔드이다. 이름 상수와 payload 클래스는 `messaging/contract` 패키지에 있다.
실제 Exchange/Queue 선언과 리스너는 6단계(RabbitMQ 구현)에서 이 문서대로 만든다.

## 왜 큐를 쓰는가

- LLM 분석은 수 초~수십 초가 걸린다. `POST /api/rfqs`는 큐에 넣고 바로 202를 돌려준다.
- Agent 서버가 일시적으로 죽어 있어도 요청이 사라지지 않고, 소비자가 재시도한다.
- 소비자 동시 실행 수(`concurrency`)로 동시에 도는 LLM 분석 수를 조절한다. 큐가 완충 역할을 한다.

**큐가 하는 일은 "분석을 시작해라"와 "상태가 바뀌었다"를 전달하는 것까지이다.** 데이터를 나르지 않고, DB 접근도 하지 않는다.
Vue↔Spring, Agent↔MCP, MCP→Spring, SSE는 모두 HTTP이며 큐를 거치지 않는다.

## 흐름

```
Vue ──POST /api/rfqs──▶ Spring(생산자) ──analysis.requested──▶ [analysis.request.q]
                                                                      │
                                                    Spring(소비자) ◀──┘ → Agent 서버 호출(내부 HTTP, 202)
                                                                      │
Agent ──POST /internal/analysis-results──▶ Spring: 저장 후 analysis.completed 발행
                                                                      ▼
                                                            [analysis.result.q]
                                                                      │
                                            Spring(소비자) ◀──────────┘ → 작업 상태 갱신 + SSE 중계

요구 확인·승인·반려 API ──analysis.resume──▶ [analysis.request.q] ──▶ 소비자 → Agent resume API
```

## Exchange / Queue / Routing key

| 이름 | 종류 | 비고 |
| --- | --- | --- |
| `supplysync.analysis` | topic exchange, durable | 정상 경로 |
| `supplysync.analysis.dlx` | direct exchange, durable | 실패 메시지 |
| `analysis.request.q` | queue, durable | 바인딩 `analysis.requested`, `analysis.resume`. 실패 시 DLX로 |
| `analysis.request.dlq` | queue, durable | 재시도를 모두 소진한 요청 |
| `analysis.result.q` | queue, durable | 바인딩 `analysis.completed`, `analysis.failed` |
| `analysis.result.dlq` | queue, durable | 처리 못 한 결과 이벤트 |
| `document.embed.q` | queue, durable | 바인딩 `document.embed.requested`. 실패 시 DLX로 |
| `document.embed.dlq` | queue, durable | 임베딩을 끝내지 못한 요청. 해당 청크는 `embedding_status = FAILED` |

## 메시지 봉투

모든 메시지는 JSON이고 `delivery_mode=persistent`로 보낸다.

```json
{
  "messageId": "0b6c7a5e-2f6c-4a52-9d57-0d8f2b4f9a10",
  "requestId": "7d9f3c4a-1b2e-4c3d-8a9b-5e6f7a8b9c0d",
  "type": "analysis.requested",
  "schemaVersion": 1,
  "occurredAt": "2026-10-19T01:00:00Z",
  "payload": { }
}
```

| 필드 | 값의 출처 | 의미 |
| --- | --- | --- |
| `messageId` | 발행할 때마다 새로 만드는 UUID | 소비자가 **브로커의 재전달**로 같은 메시지를 두 번 받은 것을 거르는 키 |
| `requestId` | `ai_tasks.request_id` | **업무 요청**의 멱등 키. 우리 쪽이 같은 작업을 다시 발행해도 같은 값 |
| `type` | 고정 문자열 | routing key와 같은 값 |
| `schemaVersion` | 상수 `1` | 호환이 깨질 때만 올린다. 모르는 버전은 소비자가 거절(DLQ) |
| `occurredAt` | 발행 시각(UTC) | 지연 측정, 오래된 메시지 판단 |

### AMQP 속성 (본문 밖)

| 속성 | 값 |
| --- | --- |
| exchange / routing key | `supplysync.analysis` / 메시지 `type` |
| `content_type` | `application/json` |
| `delivery_mode` | 2 (persistent). 브로커가 재시작돼도 디스크에 남는다 |
| `message_id` | 봉투의 `messageId` |
| `correlation_id` | `requestId`. 로그에서 한 요청을 끝까지 추적한다 |
| 헤더 | `messageId`, `requestId`, `schemaVersion` 복사. 본문을 열지 않고 확인하기 위해서다 |
| 발행 옵션 | `mandatory=true` + publisher confirm. 큐에 도착하지 못하거나 브로커가 거부하면 즉시 예외로 알아챈다 |

## 메시지 종류

### `analysis.requested`

- 생산자: RFQ 등록 API. `rfqs`, `ai_tasks`를 저장하고 **커밋한 뒤에** 발행한다.
- 소비자: Spring. Agent 서버에 작업 실행을 요청하고 `ai_tasks.status`를 `RUNNING`으로 바꾼다.

| payload 필드 | 타입 | 값의 출처 | 소비자가 쓰는 곳 |
| --- | --- | --- | --- |
| `taskId` | UUID | `ai_tasks.id` | 작업 조회, 상태 변경, Agent 호출의 식별자 |
| `rfqId` | number | `rfqs.id` | DB에서 요청서 원문을 읽는 키 |
| `requestedBy` | number | `ai_tasks.requested_by` | 감사 기록, 알림 대상 |
| `threadId` | string | `ai_tasks.thread_id` | Agent가 상태를 저장·재개하는 키. 형식은 `task-{taskId}`를 제안 (미확정) |

```json
"payload": {
  "taskId": "3f2b8c1e-5a4d-4e7b-9c1a-2d6f8e0b7a11",
  "rfqId": 101,
  "requestedBy": 1,
  "threadId": "task-3f2b8c1e-5a4d-4e7b-9c1a-2d6f8e0b7a11"
}
```

**요청서 원문과 사용자 정보는 payload에 넣지 않는다.**
큐는 DLQ와 관리 콘솔에서 사람 눈에 보이는데 고객사 견적 원문이 남으면 안 된다. 원문이 커지거나 바뀌어도 메시지 형식이 흔들리지 않고,
큐에서 기다리는 동안 데이터가 바뀌어도 소비자는 처리 시점의 최신 DB 값을 읽는다.

소비자 처리 순서:

```
1. schemaVersion 확인   → 모르는 버전이면 거절(DLQ)
2. messageId 중복 확인   → processed_messages INSERT가 충돌하면 ack 후 종료
3. 상태 전이(원자적)     → UPDATE ai_tasks SET status='RUNNING' WHERE id=:taskId AND status='QUEUED'
                          영향 행이 0건이면 이미 처리됨 → ack 후 종료
4. 원문 조회             → rfqs.content, rfq_number, title (taskId→rfqId로 조회)
5. Agent 호출            → POST agent:8000/runs {taskId, threadId, requestId, rfq:{...}}
                          202면 ack. 5xx·타임아웃이면 예외 → 재시도 → DLQ
```

`messageId`와 `requestId`를 둘 다 두는 이유: `messageId`는 **브로커가 같은 메시지를 재전달**한 경우를 막고,
`requestId`와 3번의 상태 조건(`status='QUEUED'`)은 **우리가 같은 작업을 다시 발행**한 경우를 막는다. 예를 들어 QUEUED로 오래 남은 작업을
점검 작업이 재발행하면 `messageId`는 새로 만들어지므로 상태 조건이 최종 방어선이다.

### `analysis.resume`

사람의 판단(요구 확인, 승인, 반려) 뒤 멈춰 있던 Agent를 같은 thread로 재개한다. `analysis.requested`와 같은 큐로 들어간다.

- 생산자: 요구 확인 API(`POST /api/ai/tasks/{id}/confirmation`), 승인 API(`POST /api/ai/tasks/{id}/approval`) 처리 직후
- 소비자: Spring. Agent 서버의 resume API(`POST agent:8000/runs/{threadId}/resume`)를 호출한다.

| payload 필드 | 타입 | 설명 |
| --- | --- | --- |
| `taskId` | UUID | `ai_tasks.id` |
| `rfqId` | number | `rfqs.id` |
| `threadId` | string | LangGraph thread id |
| `kind` | string | `CLARIFICATION`(요구 확인 후), `APPROVED`(승인 후: 재검증과 등록), `REJECTED`(반려 후: 해당 단계로 복귀) |

사람이 입력한 값(가정 값, 승인 의견)은 메시지에 싣지 않고, 소비자가 DB(`approvals`, 확인된 가정)에서 읽는다.

### `document.embed.requested`

문서 청크(`doc_chunks`)를 임베딩하도록 요청한다. 시드(`03_doc_chunks.sql`)가 청크를 `PENDING`으로 넣고, 이 메시지가 임베딩을 시작시킨다.

- 생산자: 시드 적재 직후, 문서가 바뀌었을 때(`content_hash` 변경), 재색인 요청 API
- 소비자: Spring `EmbeddingService`. PENDING(또는 `content_hash`가 바뀐) 청크를 배치로 모아 AI 서버 `POST /embeddings`(bge-m3)로 보내고, 받은 벡터를 저장한 뒤 `embedding_status = DONE`, `embedding_model`, `embedded_at`을 기록한다.

| payload 필드 | 타입 | 설명 |
| --- | --- | --- |
| `chunkIds` | string[] | 대상 청크. **비어 있으면 PENDING 청크 전체** |

- 청크 내용은 메시지에 싣지 않고 소비자가 DB에서 읽는다.
- 응답 벡터의 **차원이 1024가 아니면 저장하지 않고 실패**로 처리한다 (`doc_chunks.embedding`이 `vector(1024)`).
- 같은 `content_hash`의 청크는 다시 임베딩하지 않는다 (멱등).
- 재시도 3회 후에도 실패하면 DLQ로 보내고 청크를 `FAILED`로 표시한다.

### `analysis.completed`

- 생산자: `POST /internal/analysis-results` 저장 직후 (이후 DONE 전환 때도 같은 형식으로 발행하는 안)
- 소비자: Spring. `ai_tasks.status` 갱신, 승인 대기 알림(SSE `APPROVAL_REQUIRED`) 중계.

### `analysis.failed`

- 생산자: Agent 호출이 재시도를 소진했거나 Agent가 실패를 보고했을 때
- 소비자: `ai_tasks.status = FAILED`, `failure_reason` 저장, SSE `FAILED` 중계.

`analysis.completed`/`analysis.failed` 공통 payload (`AnalysisResultPayload`):

| payload 필드 | 타입 | 설명 |
| --- | --- | --- |
| `taskId` | UUID | |
| `rfqId` | number | |
| `taskStatus` | string | `TaskStatus` 값 |
| `analysisResultId` | number \| null | completed일 때만 |
| `resultVersion` | number \| null | completed일 때만 |
| `failureReason` | string \| null | failed일 때만 |

## 신뢰성 규칙

| 항목 | 규칙 |
| --- | --- |
| 전달 보장 | at-least-once. 같은 메시지가 두 번 올 수 있다 |
| 소비자 멱등성 | `processed_messages(message_id, consumer)` INSERT와 상태 전이를 **한 트랜잭션**으로 처리하고, 커밋한 뒤 ack한다 |
| ACK | Spring AMQP 기본(정상 반환이면 ack, 예외면 nack) + 아래 재시도 |
| 재시도 | 소비자 예외 시 최대 3회, 지수 백오프 (1초, 2초, 4초). 모두 실패하면 reject(requeue=false)로 DLQ |
| DLQ | 원인 확인 후 수동 재처리. 헤더 `x-death`에서 원래 큐와 횟수를 본다 |
| 순서 | 보장하지 않는다. 한 `taskId`의 이벤트는 상태 전이 규칙으로 검증하고, 이미 지난 상태로의 되돌림은 무시한다 |
| 발행 시점 | DB 커밋 후 발행(`AFTER_COMMIT`). Agent 호출 같은 긴 HTTP 호출은 DB 트랜잭션 밖에서 한다 |
| 발행 실패 대비 | 커밋은 됐는데 발행이 실패하면 작업이 QUEUED로 남는다. 일정 시간 지난 QUEUED 작업을 다시 발행하는 점검 작업을 두고, 더 강한 보장이 필요하면 outbox 테이블을 추가한다 |
| 동시성 | 소비자 `concurrency`와 `prefetch`로 동시 분석 수를 제한한다 (예: 2~3) |

## 아직 결정되지 않은 것

- Agent 서버가 완료 후 직접 `/internal/analysis-results`를 호출하는 방식(현재 가정)과, Agent가 큐에 직접 결과를 발행하는 방식 중 무엇을 쓸지. 현재 계약은 Agent가 큐를 몰라도 되도록 전자로 잡았다.
- Agent→Spring 진행 이벤트(TOOL_START 등) 경로. 큐를 거치지 않고 HTTP(`POST /internal/tasks/{id}/events`)로 보내는 안을 가정하지만 Agent 팀과 정해야 한다.
- Agent 서버의 실행·재개 API(`POST /runs`, `POST /runs/{threadId}/resume`) 규격. Agent 팀이 제공한다.
- 승인 후 재검증·등록 단계 자체를 큐로 비동기 처리할지. 지금은 재개 메시지 이후를 Agent가 HTTP로 진행하는 것으로 둔다.
