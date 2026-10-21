# 견적 승인·수정 흐름 계약

지영님 확인 문서(`개인폴더/AI_to_Backend_승인흐름_REVISE_확인.md`)에 맞춘 백엔드 계약이다.

## 프론트엔드 → Spring

`POST /api/ai/tasks/{taskId}/approval` (MANAGER Bearer JWT)

```json
{"quote_id":"Q-2026-1019-001","decision":"REVISE","reason":"보증 조건 문구 수정"}
```

- `decision`: `APPROVED`, `REJECTED`, `REVISE`.
- `REJECTED`와 `REVISE`에는 `reason`이 필수다. `REJECTED`는 작업 종료를 요청하고, `REVISE`는 SG3에서 새 견적을 작성한다.
- 최초 견적 이후 REVISE는 최대 **3회**다. 네 번째 REVISE는 409를 반환한다.
- `changes`, `return_to_step` 입력은 현재 계약에 없다. 데모에서는 reason만 Agent에 전달한다.
- `GET /api/ai/tasks/{taskId}`의 `status=DONE`일 때 `outcome=REGISTERED|REJECTED`로 최종 결과를 구분한다. 처리 중 `outcome`은 null이다.
- 승인 대기 화면은 `GET /api/rfqs/{rfqId}/analysis`의 최신 견적 `quote_id`를 사용한다. REVISE 뒤 새 견적은 별도 `quote_id`이며 이전 견적은 REJECTED 이력으로 남는다.

## Spring → Agent

`POST /runs/{threadId}/resume` (`X-Internal-Api-Key`)

```json
{"step":"SG4.request_approval","userId":2,"decision":{"result":"REVISE","reason":"보증 조건 문구 수정","changes":{}}}
```

`APPROVED`는 `decision.result=APPROVED`, `REJECTED`는 `result=REJECTED`와 `reason`을 보낸다. `REVISE`는 위와 같이 빈 `changes`와 `reason`을 보낸다. 경로의 `threadId`는 Spring이 발급한 `task-{UUID}`이다. 요구 확인(CLARIFICATION) 재개 요청은 별도 기존 형식을 사용한다.

## Agent → Spring

`POST /internal/quotes`의 본문에 `revision`을 필수로 보낸다. 최초 견적은 1, REVISE마다 1 증가하며 최대 4다. 같은 `thread_id`와 `revision`의 재시도는 기존 `quote_id`를 반환한다. 새 revision은 이전 견적에 REVISE 결정이 기록된 뒤에만 저장된다.

`POST /internal/tasks/{taskId}/events` (`X-Internal-Api-Key`)로 종료를 알린다. 경로 `taskId`는 Spring이 발급한 UUID다. 본문의 `data` 대신 `payload`도 허용한다.

```json
{"type":"RUN_END","data":{"outcome":"REJECTED"}}
```

등록 완료라면 `outcome=REGISTERED`를 보낸다. 등록 API는 견적을 REGISTERED, 작업을 REGISTERING으로 만들고, `RUN_END`가 작업을 DONE으로 확정한다. 종료 이벤트 재전송은 같은 outcome이면 멱등 처리한다. 실패는 `RUN_FAILED`를 보낸다. 종료 결과는 작업 상태 조회와 SSE 종료 이벤트에 포함된다.

## DB 변경

Flyway V4에서 `quotes.revision`, `quotes.revision_requested`, `ai_tasks.outcome`을 추가한다. `review_id + revision`에 유니크 인덱스가 있으며 기존 견적은 작성 순서대로 최대 4건까지 revision을 채운다. 기존 실행의 과거 견적에는 `revision_requested=false`이므로 새 REVISE는 이 변경 이후 생성된 견적부터 지원한다. 운영 Supabase에는 배포 시 Flyway가 이 마이그레이션을 적용해야 한다.
