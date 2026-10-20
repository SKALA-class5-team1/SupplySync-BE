# SupplySync 데이터베이스 (PostgreSQL 16 + pgvector)

현재 스키마는 `V1__init_schema.sql`에 `V2__align_dataset_v2.sql`을 적용한 결과이다.
**데이터셋 v2.2의 테이블 13개는 `01_schema.sql`과 컬럼·타입·제약·인덱스까지 동일하다.** 두 DB를 만들어 `information_schema`, `pg_constraint`, `pg_indexes`를 비교해서 확인했다 (184줄 일치, 빈 DB와 V1 데이터가 있던 DB 모두).
구조 다이어그램은 [06-erd.png](diagrams/06-erd.png), 설계 배경은 [DESIGN.md](DESIGN.md) 2장·6장을 본다.

## 테이블 구성 (16개)

| 구분 | 테이블 | 비고 |
| --- | --- | --- |
| **데이터셋 — 사용자** | `companies`, `users` | 고객사·공급사, 로그인 계정(BCrypt). CUSTOMER는 CUSTOMER 회사, MANAGER는 SUPPLIER 회사 소속 (애플리케이션에서 검증) |
| **데이터셋 — 제품·부품** | `models`, `options`, `model_options`, `parts`, `bom`, `inventory`, `part_substitutes` | 가용 재고 = `on_hand - allocated` (저장하지 않고 계산) |
| **데이터셋 — Agent 결과** | `rfqs`, `rfq_reviews`, `quotes` | 쓰기는 모두 Spring `/internal/**`를 거친다. `rfq_reviews`는 **내부용**, `quotes`는 고객이 REGISTERED만 조회 |
| **데이터셋 — 문서** | `doc_chunks` | `vector(1024)` (bge-m3), HNSW 인덱스, `embedding_status`(PENDING/DONE/FAILED), `content_hash` |
| **백엔드 전용** | `ai_tasks`, `quote_daily_seq`, `processed_messages` | 비동기 작업 추적, 견적 번호, RabbitMQ 소비자 멱등 |

## 데이터셋 테이블의 핵심

| 테이블 | 핵심 컬럼 | 비고 |
| --- | --- | --- |
| `rfqs` | `rfq_number`(UNIQUE), `customer_company_id`, `uploaded_by`, `file_path`, `status`(RECEIVED/IN_REVIEW/QUOTED), 추출 컬럼(`eau`, `sample_qty`, `sample_due_date`, `sop_date`, `target_price_krw`, `quote_due_date`) | 추출 컬럼은 SG1이 요청서를 읽은 뒤 채운다 |
| `rfq_reviews` | `(rfq_number, thread_id)` UNIQUE, `status`(RUNNING/WAITING/DONE), SG1(`selected_model`, `options`, `fit_type`, `issues`, `passed_checks`, `assumptions`, `confirmed_by/at`, `customer_questions`, `contract_risks`), SG2(`shortages`, `alternatives`, `recommended_alt`) | `rfq_number`는 `rfqs.rfq_number`와 문자열로만 연결(FK 없음). upsert 기준은 `(rfq_number, thread_id)` |
| `quotes` | `quote_id`(`Q-YYYY-MMDD-NNN`), `review_id`, `unit_price_krw`(고객 공개), `parts_cost_krw`·`margin_pct`·`checks`(**내부용**), `price_by_year`, `conditions`, `status`(DRAFT/APPROVED/REJECTED/REGISTERED), `approved_by`, `reject_reason`, `registered_at` | 반려·조건 변경 시 이전 견적은 REJECTED로 두고 새 `quote_id`의 DRAFT를 만든다 |
| `doc_chunks` | `chunk_id`, `doc_type`(design_std/catalog/policy/alt_parts), `content`, `embedding`, `embedding_status`, `embedding_model`, `embedded_at`, `content_hash` | 검색은 `embedding IS NOT NULL`만 대상. `content_hash`가 바뀌면 재임베딩 |

타임스탬프는 데이터셋 테이블이 `TIMESTAMP`(타임존 없음), 백엔드 전용 테이블이 `TIMESTAMPTZ`이다.

## 백엔드 전용 테이블

| 테이블 | 컬럼 | 용도 |
| --- | --- | --- |
| `ai_tasks` | `task_id`(UUID PK), `rfq_number`(FK), `requested_by`(FK), `status`, `current_step`, `thread_id`(UNIQUE), `request_id`(UNIQUE), `failure_reason`, `trace_url`, 시각 | Agent 실행 1회 = 작업 1건. 상태 9종: QUEUED, RUNNING, NEEDS_CLARIFICATION, WAITING_APPROVAL, REVALIDATING, REGISTERING, ON_HOLD, DONE, FAILED. `thread_id`로 `rfq_reviews`와 연결 |
| `quote_daily_seq` | `day`(PK), `last_seq` | `quote_id` 일별 번호. `INSERT ... ON CONFLICT DO UPDATE ... RETURNING`으로 원자적 증가 |
| `processed_messages` | `(message_id, consumer)` PK | RabbitMQ 소비자가 같은 메시지를 두 번 처리하지 않도록 기록 |

## V2에서 한 일 (V1은 이미 푸시되어 수정할 수 없다)

| 대상 | 조치 |
| --- | --- |
| `companies`, `users`, `rfqs`, `rfq_reviews`, `quotes`, `doc_chunks` | V1 테이블 삭제 후 **데이터셋 정의 그대로** 재생성 (스크립트로 `01_schema.sql`에서 추출해서 조립) |
| `part_substitutes` | `compatibility_note`, `requires_spec_revalidation` 삭제 → `pin_compatible`, `ref_chunk_id` 추가, `validation_days` 기본값 제거 |
| `bom`, `inventory` | 수량 CHECK 추가, `incoming_qty` 기본값 0 |
| `models`, `options`, `model_options`, `parts`, `bom`, `inventory`, `part_substitutes` | V1의 데모 데이터만 비움 (새 시드가 채운다) |
| `analysis_results`, `approvals`, `quote_items`, `purchase_requests`, `purchase_request_items`, `registration_requests` | **삭제.** `rfq_reviews`·`quotes`가 대체한다. 구매요청은 데이터셋에 없다 (범위 결정 필요) |
| `ai_tasks` | 새 구조로 재생성 (`rfq_number` FK) |
| `quote_daily_seq` | 신규 |
| `processed_messages` | 유지 |

## 설계 규칙

- **상태 전이는 조건부 UPDATE**로 한다. 영향 행이 0건이면 이미 처리된 것이다 (승인 더블 클릭, 등록 재시도). 이 패턴이 스키마에서 동작함을 테스트가 확인한다.
- **중복 처리 방지:** 견적은 `quotes.status`의 `APPROVED → REGISTERED` 조건부 UPDATE로, RFQ 접수·분석 요청은 `ai_tasks.request_id`(UNIQUE)로 막는다.
- **분석 결과 갱신은 섹션 단위 upsert**이다. SG1이 `issues`, `selected_model`을 먼저 저장하고 SG2가 `shortages`, `alternatives`를 이어서 저장한다. 전달하지 않은 섹션은 유지한다.
- **내부용 필드는 고객 응답에 포함하지 않는다.** 필드를 숨기지 않고 DTO를 분리한다.
- 상태 값은 PG enum 대신 `VARCHAR + CHECK`를 쓴다. Java enum(`domain` 패키지)과 값을 맞춘다.

## 아직 정해지지 않은 것

- **구매요청:** 데이터셋에 테이블이 없다. 데이터셋 담당 확인 결과 의도한 제외가 아니라 빠진 것이어서 **결정이 필요**하다.
- **`quotes`에 컬럼이 없는 견적 필드:** `recipient`, `item`, `incoterms`, `issue_date`, `price_breakdown`, `parts_cost_breakdown`. 요청에는 포함되지만 저장하지 않으며, `rfqs`·카탈로그·발행 시각으로 다시 만들 수 있다. 저장 위치를 정해야 한다.
- 청크 임베딩 적재 파이프라인과 AI 서버 `/embeddings` 제공 시점.
