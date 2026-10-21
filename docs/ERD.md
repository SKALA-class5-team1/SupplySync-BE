# SupplySync 데이터베이스 (PostgreSQL 16 + pgvector)

현재 스키마는 [`V2__dataset_v2_2.sql`](../src/main/resources/db/migration/V2__dataset_v2_2.sql)과 후속 V3(Agent 결과), V4(견적 revision·작업 outcome), V5(분석 outbox)를 적용한 결과이다.
**데이터셋 v2.2의 테이블 13개는 v2에서 `01_schema.sql` 정의를 그대로 포함한다.** 별도로 백엔드 운영 테이블 3개를 뒤에 추가하고, v3에서 Agent 결과 저장 컬럼 3개를 확장한다.
구조 다이어그램은 [06-erd.png](diagrams/06-erd.png), 설계 배경은 [DESIGN.md](DESIGN.md) 2장·6장을 본다.

## 테이블 구성 (17개)

| 구분 | 테이블 | 비고 |
| --- | --- | --- |
| **데이터셋 — 사용자** | `companies`, `users` | 고객사·공급사, 로그인 계정(BCrypt). CUSTOMER는 CUSTOMER 회사, MANAGER는 SUPPLIER 회사 소속 (애플리케이션에서 검증) |
| **데이터셋 — 제품·부품** | `models`, `options`, `model_options`, `parts`, `bom`, `inventory`, `part_substitutes` | 가용 재고 = `on_hand - allocated` (저장하지 않고 계산) |
| **데이터셋 — Agent 결과** | `rfqs`, `rfq_reviews`, `quotes` | 쓰기는 모두 Spring `/internal/**`를 거친다. `rfq_reviews`는 **내부용**, `quotes`는 고객이 REGISTERED만 조회 |
| **데이터셋 — 문서** | `doc_chunks` | `vector(1024)` (bge-m3), HNSW 인덱스, `embedding_status`(PENDING/DONE/FAILED), `content_hash` |
| **백엔드 전용** | `ai_tasks`, `quote_daily_seq`, `processed_messages`, `analysis_outbox` | 비동기 작업 추적, 견적 번호, RabbitMQ 소비자 멱등·발행 복구 |

## 데이터셋 테이블의 핵심

| 테이블 | 핵심 컬럼 | 비고 |
| --- | --- | --- |
| `rfqs` | `rfq_number`(UNIQUE), `customer_company_id`, `uploaded_by`, `file_path`, `status`(RECEIVED/IN_REVIEW/QUOTED), 추출 컬럼(`eau`, `sample_qty`, `sample_due_date`, `sop_date`, `target_price_krw`, `quote_due_date`) | 추출 컬럼은 SG1이 요청서를 읽은 뒤 채운다 |
| `rfq_reviews` | `(rfq_number, thread_id)` UNIQUE, `status`(RUNNING/WAITING/DONE), SG1(`selected_model`, `options`, `fit_type`, `issues`, `passed_checks`, `assumptions`, `confirmed_by/at`, `customer_questions`, `contract_risks`), SG2(`shortages`, `alternatives`, `recommended_alt`), v3 `final_plan`(JSONB) | `rfq_number`는 `rfqs.rfq_number`와 문자열로만 연결(FK 없음). upsert 기준은 `(rfq_number, thread_id)` |
| `quotes` | `quote_id`(`Q-YYYY-MMDD-NNN`), `review_id`, `unit_price_krw`(고객 공개), `parts_cost_krw`·`margin_pct`·`checks`(**내부용**), `price_by_year`, `conditions`, `status`(DRAFT/APPROVED/REJECTED/REGISTERED), `approved_by`, `reject_reason`, `registered_at`, v3 `issue_date`(DATE)·`details`(JSONB), v4 `revision`·`revision_requested` | `details`의 부품 원가 구성은 담당자에게만 반환. REVISE일 때 이전 견적은 REJECTED로 두고 새 `quote_id`의 DRAFT를 만든다. `review_id + revision`은 유일하다 |
| `doc_chunks` | `chunk_id`, `doc_type`(design_std/catalog/policy/alt_parts), `content`, `embedding`, `embedding_status`, `embedding_model`, `embedded_at`, `content_hash` | 검색은 `embedding IS NOT NULL`만 대상. `content_hash`가 바뀌면 재임베딩 |

타임스탬프는 데이터셋 테이블이 `TIMESTAMP`(타임존 없음), 백엔드 전용 테이블이 `TIMESTAMPTZ`이다.

## 백엔드 전용 테이블

| 테이블 | 컬럼 | 용도 |
| --- | --- | --- |
| `ai_tasks` | `task_id`(UUID PK), `rfq_number`(FK), `requested_by`(FK), `status`, v4 `outcome`(REGISTERED/REJECTED), `current_step`, `thread_id`(UNIQUE), `request_id`(UNIQUE), `failure_reason`, `trace_url`, 시각 | Agent 실행 1회 = 작업 1건. 상태 9종: QUEUED, RUNNING, NEEDS_CLARIFICATION, WAITING_APPROVAL, REVALIDATING, REGISTERING, ON_HOLD, DONE, FAILED. `thread_id`로 `rfq_reviews`와 연결 |
| `quote_daily_seq` | `day`(PK), `last_seq` | `quote_id` 일별 번호. `INSERT ... ON CONFLICT DO UPDATE ... RETURNING`으로 원자적 증가 |
| `processed_messages` | `(message_id, consumer)` PK | RabbitMQ 소비자가 같은 메시지를 두 번 처리하지 않도록 기록 |
| `analysis_outbox` | `message_id` PK, `request_id`, `type`, `body`, `published_at`, `attempt_count`, `last_error` | 업무 트랜잭션과 함께 기록한 분석 명령을 RabbitMQ 확인 후 발행 완료 처리 |

## v2 데이터셋 마이그레이션과 후속 확장

확정 데이터셋의 `01_schema.sql`을 바탕으로 빈 업무 스키마에 13개 테이블을 만든다. 이어서 `ai_tasks`, `quote_daily_seq`, `processed_messages`를 만든다. Supabase의 `vector` 확장이 `extensions` 스키마에 있어도 동작하도록 마이그레이션 안에서 검색 경로를 설정한다.

Supabase의 `public` 스키마에는 기본 객체가 이미 있으므로 첫 적용 시 Flyway가 기존 스키마를 **버전 0으로 기록**한 후 **버전 2와 3**을 적용한다. 버전 0은 테이블 정의나 시연 데이터가 아니다. v3은 이미 적용한 v2 파일을 수정하지 않고 `final_plan`, `issue_date`, `details`를 추가한다. 실제 시연 행은 `demo` 프로파일의 원본 `02_seed.sql`, `03_doc_chunks.sql`에서만 적재된다. 구매요청 테이블은 만들지 않는다.

## 설계 규칙

- **상태 전이는 조건부 UPDATE**로 한다. 영향 행이 0건이면 이미 처리된 것이다 (승인 더블 클릭, 등록 재시도). 이 패턴이 스키마에서 동작함을 테스트가 확인한다.
- **중복 처리 방지:** 견적은 `quotes.status`의 `APPROVED → REGISTERED` 조건부 UPDATE로, RFQ 접수·분석 요청은 `ai_tasks.request_id`(UNIQUE)로 막는다.
- **분석 결과 갱신은 섹션 단위 upsert**이다. SG1이 `issues`, `selected_model`을 먼저 저장하고 SG2가 `shortages`, `alternatives`를 이어서 저장한다. 전달하지 않은 섹션은 유지한다.
- **내부용 필드는 고객 응답에 포함하지 않는다.** 필드를 숨기지 않고 DTO를 분리한다.
- 상태 값은 PG enum 대신 `VARCHAR + CHECK`를 쓴다. Java enum(`domain` 패키지)과 값을 맞춘다.

## 아직 정해지지 않은 것

- **구매요청:** 이번 구현 범위에서 제외한다. 견적 등록이 최종 업무 결과다.
- **견적 상세 저장:** `recipient`, `incoterms`, `price_breakdown`, `parts_cost_breakdown`은 `quotes.details`에 저장한다. `issue_date`는 별도 DATE 컬럼이다. `item`, `eau`, `sop_date`는 RFQ에서 조회할 수 있어 견적에 중복 저장하지 않는다.
- 팀이 이미 적재한 벡터와 원본 데모 시드(`PENDING`)의 환경별 차이. 새 문서는 Agent 팀 RAG 적재 엔드포인트로 전달할 예정이며 DB 쓰기 계정·권한은 확정해야 한다.
