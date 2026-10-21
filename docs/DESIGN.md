# SupplySync 백엔드 설계 (확정 데이터셋 v2.2)

## 목표와 범위

고객 RFQ를 접수하고 Agent가 사양 적합성, 부품 재고, 조달 대안, 견적을 검토한다. 담당자가 요구사항을 확인하고 견적을 승인하면 최신 조건을 재검증해 견적을 등록한다. **구매요청 등록은 이번 범위에서 제외한다.** 프론트 API 계약은 전달되었고 프론트 구현이 진행될 예정이다.

기준 데이터는 `개인폴더/Input/rfq_dataset_v2.2` 하나다. 백엔드의 시드 SQL과 RFQ 원문은 그 데이터셋의 복사본이며 다른 시연 데이터는 적재하지 않는다. `05_expected` JSON은 스텁 응답과 테스트의 기준이며 DB 시드가 아니다.

## 현재 구현 상태

| 영역 | 상태 |
| --- | --- |
| DB | V2에 데이터셋 13개·운영 3개 테이블, V3에 Agent 결과 컬럼 3개, V4에 견적·작업 결과 컬럼, V5에 RabbitMQ outbox 테이블 추가 |
| Supabase | PostgreSQL 연결, `vector` 확장 스키마 대응, Flyway baseline 0 → v2 적용, v2.2 시드 적재 확인 |
| API | 카탈로그·RFQ 조회와 B1 저장·상태 전이·SSE 구현. BCrypt 로그인·JWT 발급·현재 사용자·역할 및 회사별 접근 제한 구현 |
| 공통 | 상태 enum, 통일된 오류 응답, RabbitMQ 요청·재개 경로 구현(기본 비활성) |
| 미구현 | Agent 서버와 실제 HTTP 계약 검증, RabbitMQ 실브로커·Agent 통합 검증, 새 문서 수신·RAG 적재 전달 API |

## 저장소

Supabase PostgreSQL에 업무 데이터와 pgvector 문서 청크를 둔다. 프론트는 Spring의 `/api/**`를 호출하고, Agent·business MCP는 `/internal/**`를 사용한다. RAG 검색과 새 문서의 임베딩·벡터 적재는 Agent 팀이 맡을 예정이다. Spring은 새 문서를 받으면 RAG 적재 엔드포인트로 전달한다. RabbitMQ는 Spring의 Agent 분석 작업 실행·재개에 사용한다. [연동 구조](RAG_MCP_INTEGRATION.md)에 계약 항목을 적었다.

### 테이블 17개

| 구분 | 테이블 |
| --- | --- |
| 데이터셋: 사용자 | `companies`, `users` |
| 데이터셋: 제품·부품 | `models`, `parts`, `options`, `model_options`, `bom`, `inventory`, `part_substitutes` |
| 데이터셋: RFQ·결과 | `rfqs`, `rfq_reviews`, `quotes` |
| 데이터셋: RAG 문서 | `doc_chunks` (`vector(1024)`, 원본 데모 시드의 초기 상태 `PENDING`; 실제 벡터 DB는 팀이 벡터 적재 완료) |
| 백엔드 운영 | `ai_tasks`, `quote_daily_seq`, `processed_messages`, `analysis_outbox` |

자세한 컬럼과 상태는 [ERD](ERD.md)를 참고한다. Flyway v2는 데이터셋 `01_schema.sql`을 기준으로 13개 테이블을 만들고 운영 테이블 3개를 추가한다. V5가 outbox 테이블을 추가한다. Supabase의 기본 `public` 객체 때문에 처음에는 baseline 버전 0을 기록한다. 버전 0은 추가 데이터셋이 아니다.

데모 적재는 `demo` 프로파일에서만 수행한다. 데이터셋 원본 `02_seed.sql`, `03_doc_chunks.sql`, RFQ 파일을 복사한 리소스를 사용한다. `models`가 비어 있을 때만 SQL을 적재해 재시작 시 중복 삽입하지 않는다. 데모 계정은 `hb_buyer01`(CUSTOMER), `ds_sales01`(MANAGER)이다.

## 업무 흐름

1. 고객이 RFQ 원문 파일을 업로드한다. Spring이 파일과 `rfqs`, `ai_tasks`를 저장한다.
2. MQ 활성화 시 Spring이 outbox를 거쳐 RabbitMQ에 분석 요청을 발행하고, 소비자가 Agent 실행 API를 호출한다. 기본값에서는 기존 HTTP 어댑터를 사용한다.
3. Agent는 business MCP와 RAG MCP로 모델·재고·규정 근거를 조회한다. 진행 이벤트와 검토 결과는 Spring에 전달한다.
4. 높은 심각도의 누락·모순은 담당자 확인을 기다린다. 확인 뒤 같은 Agent thread를 재개한다.
5. Agent가 부족 부품 대안과 견적 초안을 만들고, Spring이 `rfq_reviews`와 `quotes`에 저장한다.
6. 담당자는 `APPROVED`(재검증·등록), `REJECTED`(종료), `REVISE`(SG3 재작성)를 선택한다. REVISE는 최대 3회이며 새 `quote_id`를 만든다. 등록 또는 반려 후 Agent의 `RUN_END`가 작업을 DONE으로 확정한다.
7. Spring이 작업 상태와 진행 이벤트를 프론트 SSE에 중계한다.

B1의 RFQ 업로드·작업 생성, 분석 결과 부분 갱신, 견적 생성·승인·등록, 작업 이벤트·SSE가 DB 기반으로 동작한다. Agent 실행·재개는 `AGENT_BASE_URL` 설정 시 커밋 뒤 제안 HTTP 경로(`/runs`, `/runs/{threadId}/resume`)로 호출한다. Spring과 Agent는 양방향 HTTP 호출에 공유 `INTERNAL_API_KEY`를 `X-Internal-Api-Key` 헤더로 사용한다. 승인 재개·견적 revision·종료 이벤트 JSON은 [견적 승인·수정 흐름 계약](APPROVAL_REVISE_HANDOFF.md)에 정리했다. Agent 서버와의 실제 통합 검증은 아직 필요하다. 주소가 비어 있으면 작업은 `QUEUED`로 남는다. 브라우저 API는 JWT로 인증하며 고객은 소속 회사 RFQ·작업만 조회한다. RFQ 업로드, 담당자 확인·승인 기록에는 로그인한 사용자의 ID를 사용한다. SSE도 Bearer JWT가 필요하다. [프론트 인증 연동 안내](FRONTEND_AUTH_HANDOFF.md)에 요청·응답 계약을 정리했다.

## 구현 순서

| 순서 | 구현 내용 |
| --- | --- |
| B1 | 구현: 카탈로그·BOM·재고 조회, RFQ 업로드·작업 생성, 검토 결과 upsert, 견적 생성·승인·등록, Agent HTTP 어댑터, SSE 중계. 남음: Agent 서버 계약 확정·연결 검증 |
| B2 | 구현: RabbitMQ 분석 요청·재개용 토폴로지·outbox 발행·소비·멱등·재시도·DLQ(기본 비활성). 남음: 실제 브로커·Agent core 통합 검증, 새 문서 수신 후 RAG 적재 전달 |
| B3 | 구현: 로그인·JWT, 고객/담당자 권한, 회사별 접근 제한, 내부 API 키, SSE 인증 |
| B4 | 프론트·Agent·RAG MCP 통합 시나리오, 실패·재시도 검증, 운영 문서 |

## 남은 계약 결정

- RAG MCP의 검색 도구명·입출력과 새 문서 적재 HTTP 엔드포인트 계약(요청 형식·인증·응답·중복/수정 처리·DB 권한).
- Agent 실행·재개 API, 진행 이벤트 경로와 `thread_id` 규칙.
- RFQ 원문 파일의 배포 저장소. 현재는 로컬 `RFQ_FILES_DIR`이다.
- Agent팀 SG4가 `final_plan`을 어떤 API로 읽을지. 담당자 분석 API에서는 조회 가능하며 Agent 전용 읽기 경로는 계약 확인이 필요하다.

상세 다이어그램은 [diagrams](diagrams/)에 있다. 일부 그림에는 최초 로컬 PostgreSQL 배치가 남아 있으므로 실제 Supabase·RAG MCP·RabbitMQ 연결은 [연동 구조](RAG_MCP_INTEGRATION.md)를 기준으로 한다.
