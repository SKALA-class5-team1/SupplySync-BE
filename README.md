# SupplySync-BE

공급망 연계 RFQ·재고·견적 대응 Agent의 Spring Boot 백엔드. 구매요청 등록은 이번 범위에서 제외한다.
Java 21, Spring Boot 4.1.1, Supabase PostgreSQL + pgvector, RabbitMQ, Flyway, springdoc(Swagger).

## Supabase PostgreSQL로 실행

1. 새 Supabase 프로젝트에서 `vector` 확장을 `extensions` 스키마에 활성화한다. 이 마이그레이션은 확정 데이터셋 v2.2용 새 프로젝트에 적용한다.
2. `.env.supabase.example`을 `.env`로 복사하고 Dashboard **Connect**의 host, port, database, user를 그대로 입력한다. 비밀번호도 로컬 `.env`에만 입력한다. Direct 연결은 IPv6 환경에서, IPv4 환경은 Session pooler를 사용한다.
3. `docker compose up -d rabbitmq`로 RabbitMQ만 실행한다. `SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun`으로 Flyway v2~v5 마이그레이션과 확정 데이터셋 시드를 적용한다. 시드가 필요 없다면 `./gradlew bootRun`을 실행한다.
4. RAG MCP가 검색에 사용할 읽기 계정은 필요하면 Supabase에서 별도로 만든다. RAG 측이 새 문서 벡터를 직접 적재하는 경우에는 `rag_reader`의 SELECT 권한만으로는 부족하므로, Agent 팀과 적재 주체·계정을 확정한 뒤 `doc_chunks`에 한정된 권한을 설정한다.

Spring이 JDBC로 업무 테이블을 읽고 쓴다. 프론트는 기존 `/api/**` 계약을 그대로 사용한다. `vector` 확장 스키마를 찾기 위해 Supabase 설정은 `DB_SCHEMA=public,extensions`, TLS를 위해 `DB_SSLMODE=require`이다. Supabase `public`의 기본 객체 때문에 첫 적용 시 `DB_FLYWAY_BASELINE_ON_MIGRATE=true`와 baseline 버전 0을 사용한다. Flyway v2는 확정 데이터셋 스키마, v3는 Agent 결과 컬럼, v4는 견적 revision·작업 outcome, v5는 RabbitMQ outbox다.

## 로컬 PostgreSQL로 실행하기 (선택)

```bash
# 1. 환경 변수 파일 만들기 (비밀번호를 바꾼다. .env는 커밋하지 않는다)
cp .env.example .env

# 2. PostgreSQL(pgvector) + RabbitMQ 실행
docker compose up -d
docker compose ps            # 둘 다 healthy가 될 때까지 기다린다

# 3. 앱 실행 (새 DB에 단일 Flyway v2 마이그레이션 적용)
./gradlew bootRun

# 3-1. 데모 데이터까지 넣고 실행 (데이터셋 v2.2: RFQ HB-EV7-EWP-001 시나리오)
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

| 확인할 것 | 주소 |
| --- | --- |
| Swagger UI | http://localhost:8080/swagger-ui.html (상단 드롭다운: 화면용 / 내부 업무) |
| OpenAPI JSON | http://localhost:8080/v3/api-docs/1-web, `/v3/api-docs/2-internal` |
| 헬스체크 | http://localhost:8080/actuator/health |
| RabbitMQ 관리 콘솔 | http://localhost:15672 (`RABBITMQ_USER` / `RABBITMQ_PASSWORD`) |

> 기존의 오래된 로컬 DB에는 이전 마이그레이션 이력이 남아 있을 수 있다. v2 기반 스키마는 새 DB 기준이며 이미 적용한 v2 파일은 수정하지 않고 v3을 추가했다.
> 로컬 데이터를 폐기해도 되는 경우에만 DB 볼륨을 초기화하고 새로 실행한다.

## API 규칙

- **JSON 필드명은 snake_case**이다 (`rfq_number`, `unit_price_krw`). 데이터셋 정답지(`05_expected`)와 같은 이름이라 Agent·MCP·프론트가 같은 이름을 쓴다.
  Swagger 문서의 필드명도 같다 (테스트가 런타임과 문서의 일치를 확인한다).
- 카탈로그·RFQ 조회, 업로드·작업 생성, 분석 결과 upsert, 견적 생성·승인·등록, 작업 상태·SSE는 DB와 파일에 연결돼 있다. 로그인은 BCrypt 검증과 JWT 발급으로 동작한다.
- 분석 메시지 큐는 `ANALYSIS_MQ_ENABLED=true`로 켠다. 기본값 false에서는 기존 HTTP 어댑터를 사용한다. 큐를 켜려면 `AGENT_BASE_URL`과 RabbitMQ 접속 정보가 필요하다. 실제 Agent core의 요청·응답 계약과 통합 검증은 남아 있다.
- `/api/**`는 로그인 JWT가 필요하다. 고객 업로드와 담당자 확인·승인은 로그인한 사용자 ID로 기록한다. `GET /api/rfqs?mine=true`와 고객 회사별 조회 제한을 적용한다.
- `/internal/**`은 기존 `X-Internal-Api-Key`로 인증한다. Agent에 사용자 JWT를 보내지 않는다. 프론트 연동 계약은 [Frontend 문서](docs/FRONTEND.md)를 참고한다.
- 오류 응답은 `{code, message, details, timestamp}` 하나로 통일한다.

## API 명세

전체 요청·응답 스키마는 실행 중인 서버의 Swagger UI(`/swagger-ui.html`)와 OpenAPI(`/v3/api-docs`)가 기준이다. 아래는 엔드포인트 요약이다.

### 화면용 `/api/**` (Bearer JWT)

| 메서드 | 경로 | 권한 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | 공개 | `login_id`, `password`로 로그인. `access_token`, `token_type`, `expires_in`, `user` 반환 |
| GET | `/api/me` | 로그인 | 토큰의 사용자 정보 |
| POST | `/api/rfqs` | CUSTOMER | RFQ 접수. `multipart/form-data`(`file`=.md, `rfq_number`). 202, 같은 번호는 409 |
| GET | `/api/rfqs?mine=false` | 로그인 | RFQ 목록(최신순). 고객은 소속 회사 것만, `mine=true`는 본인이 올린 것만 |
| GET | `/api/rfqs/{rfqId}` | 로그인 | RFQ 상세. 다른 회사 RFQ는 404 |
| GET | `/api/rfqs/{rfqId}/analysis` | MANAGER | 분석 결과 + 원가·마진 포함 견적 이력 |
| GET | `/api/rfqs/{rfqId}/quotes` | 로그인 | 고객 공개 견적 목록. `REGISTERED`만, 원가·마진 제외 |
| GET | `/api/ai/tasks/{taskId}` | 로그인 | 작업 상태 조회 (SSE 끊김 후 복구용) |
| GET | `/api/ai/tasks/{taskId}/stream` | 로그인 | 진행 이벤트 SSE (`text/event-stream`). 고객에게는 단계·시간만 전달 |
| POST | `/api/ai/tasks/{taskId}/confirmation` | MANAGER | 요구 확인. 본문 `{"assumptions":[{"field":"spec.flow","value":"22 L/min"}]}`. 202, 대기 상태가 아니면 409 |
| POST | `/api/ai/tasks/{taskId}/approval` | MANAGER | 본문 `{"quote_id":"Q-2026-1019-001","decision":"APPROVED\|REJECTED\|REVISE","reason":"..."}`. `REJECTED`·`REVISE`는 `reason` 필수, `REVISE`는 최대 3회. 202 |

로그인 예시:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"login_id":"ds_sales01","password":"demo1234"}'
# 이후 요청: -H "Authorization: Bearer <access_token>"
```

SSE 이벤트: `STEP_START`/`STEP_END`, `TOOL_START`/`TOOL_END`, `TOKEN`, `CLARIFICATION_REQUIRED`(MANAGER만), `APPROVAL_REQUIRED`(MANAGER만), `RUN_END`, `RUN_FAILED`. 공통 data 필드는 `task_id`, `step`, `at`이다. 이슈·견적 상세는 이벤트에 없으므로 분석 API를 다시 조회한다.

### 내부용 `/internal/**` (`X-Internal-Api-Key`, Agent·MCP 전용)

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/internal/models` | 표준 모델과 옵션 목록 |
| GET | `/internal/models/{modelId}` | 표준 모델 조회 |
| GET | `/internal/models/{modelId}/bom?options=OPT-HT,OPT-IP9K` | 옵션 적용 BOM. 붙일 수 없는 옵션은 400 |
| GET | `/internal/parts/{partId}` | 부품 마스터(공급사, 원가, 조달기간) |
| GET | `/internal/parts/{partId}/inventory` | 재고·입고 예정 |
| GET | `/internal/parts/{partId}/substitutes` | 대체 부품 후보 |
| GET | `/internal/rfqs/{rfqNumber}` | RFQ 메타 정보와 추출 컬럼 |
| GET | `/internal/rfqs/{rfqNumber}/content` | RFQ 원문(`text/markdown`) |
| PATCH | `/internal/rfqs/{rfqNumber}` | SG1 추출 값 저장. 전달한 필드만 갱신. status는 `RECEIVED → IN_REVIEW → QUOTED` 순서만 허용 |
| PUT | `/internal/rfq-reviews` | 분석 결과 upsert. `(rfq_number, thread_id)` 기준, 전달한 섹션만 갱신(멱등) |
| POST | `/internal/quotes` | 견적 초안 생성(201). `quote_id`는 서버가 `Q-YYYY-MMDD-NNN`으로 생성. 분석 결과가 없으면 404 |
| PATCH | `/internal/quotes/{quoteId}` | `DRAFT`일 때만 `checks`, `conditions` 갱신. 아니면 409 |
| POST | `/internal/quotes/{quoteId}/register` | `APPROVED` 견적만 `REGISTERED`로 등록(멱등). 신규 201, 이미 등록 200, 미승인 409 |
| POST | `/internal/tasks/{taskId}/events` | Agent 진행 이벤트 전달(202). `type`, `step`, `message`, `trace_url`, `data` |

작업 상태는 이벤트로 전이한다: `CLARIFICATION_REQUIRED → NEEDS_CLARIFICATION`, `APPROVAL_REQUIRED → WAITING_APPROVAL`, `RUN_END → DONE`, `RUN_FAILED → FAILED`.

### 오류 응답

모든 오류는 `{code, message, details, timestamp}` 형식이다.

| code | HTTP | 의미 |
| --- | --- | --- |
| `VALIDATION_ERROR`, `MALFORMED_REQUEST` | 400 | 입력 검증 실패, 요청 형식 오류 |
| `UNAUTHORIZED` | 401 | 토큰 없음·만료, 로그인 실패 |
| `FORBIDDEN` | 403 | 역할 권한 없음 |
| `NOT_FOUND` | 404 | 없는 리소스 (다른 회사 리소스 포함) |
| `CONFLICT_STATE` | 409 | 상태 불일치, 이미 처리됨 |
| `AGENT_UNAVAILABLE` | 503 | Agent 호출 불가 |
| `INTERNAL_ERROR` | 500 | 서버 오류 |

## 데모 데이터 (demo 프로파일)

시드는 데이터셋 v2.2의 `02_seed.sql`, `03_doc_chunks.sql`을 **수정 없이 복사**한 것이다 (`src/main/resources/db/seed/`). 데이터셋이 바뀌면 파일을 교체한다.

| 계정 | 소속 | 역할 | 비밀번호 |
| --- | --- | --- | --- |
| `hb_buyer01` | 한별모터스 | CUSTOMER | `demo1234` |
| `ds_sales01` | 대성오토텍 | MANAGER | `demo1234` |

- 시드는 `models` 테이블이 비어 있을 때만 적재한다 (재기동해도 중복되지 않는다).
- RFQ 원문(`01_rfq/RFQ_HB-EV7-EWP-001.md`)은 `RFQ_FILES_DIR`(기본 `./rfq-files`) 아래로 복사된다. 시드의 `rfqs.file_path`는 이 디렉터리 기준 상대 경로이다.
- 원본 `03_doc_chunks.sql`을 적재한 **새 로컬·테스트 DB**의 26개 청크는 임베딩이 비어 있다(`embedding_status = PENDING`). 팀이 별도로 벡터를 적재한 실제 벡터 DB와 상태가 다르다. 앞으로 새 문서는 Spring이 수신해 Agent 팀의 RAG 적재 엔드포인트로 전달할 예정이다.

## 로컬 인프라 접속 정보

| 항목 | 값 |
| --- | --- |
| PostgreSQL | `localhost:5432` (`DB_PORT`로 변경), DB `supplysync`, 이미지 `pgvector/pgvector:pg16` |
| 앱 계정 | `DB_USER` (기본 `supplysync_app`). DB 소유자이며 superuser가 아님 |
| 관리자 계정 | `POSTGRES_ADMIN_USER` (기본 `postgres`). 앱에서는 쓰지 않음 |
| RAG 검색 계정 예시 | `RAG_DB_USER` (기본 `rag_reader`). 현재 로컬 설정은 `doc_chunks` 조회만 가능. RAG 적재 계정은 Agent 팀과 별도 확정 |
| RabbitMQ | AMQP `localhost:5672`, 관리 콘솔 `15672` |

비밀번호는 `.env`에만 있다. 팀원에게는 DM 등으로 따로 전달한다.

## 자주 쓰는 명령

```bash
docker compose down            # 중지 (데이터 유지)
docker compose down -v         # 중지 + 데이터 삭제 (DB 초기화, init 스크립트 재실행)
docker compose logs -f postgres
docker exec -it supplysync-postgres psql -U supplysync_app -d supplysync
./gradlew test                 # Docker가 켜져 있어야 한다 (Testcontainers가 pgvector PostgreSQL을 띄움)
```

- `docker/postgres/init/*.sh`는 **데이터 볼륨이 비어 있을 때 한 번만** 실행된다 (앱 계정, pgvector 확장, rag 계정 생성).
  계정 설정이나 이미지를 바꿨다면 `down -v`로 지우고 다시 띄운다.
- 호스트에 PostgreSQL이 이미 5432를 쓰고 있으면 `.env`의 `DB_PORT`를 5433 등으로 바꾼다.
- 이미 반영된 `V*.sql`을 고치면 Flyway가 체크섬 불일치 오류를 낸다. 새 `V*.sql`을 추가한다.

## 문서

| 문서 | 내용 |
| --- | --- |
| Swagger UI | 화면용 API(`/api/**`), 내부 업무 API(`/internal/**`) 계약 |
| [docs/BACKEND.md](docs/BACKEND.md) | 백엔드 업무 흐름·DB·RabbitMQ 규약·남은 검증 |
| [docs/AGENT.md](docs/AGENT.md) | Agent 실행·재개·결과 계약과 RAG 경계 |
| [docs/FRONTEND.md](docs/FRONTEND.md) | JWT·화면 권한·승인·SSE 계약 |
| [docs/diagrams/](docs/diagrams/) | 다이어그램 원본(`.mmd`)과 PNG, 적용 범위 안내 |
| [V2__dataset_v2_2.sql](src/main/resources/db/migration/V2__dataset_v2_2.sql) | 확정 데이터셋 v2.2 기반 스키마 |
| [V3__review_plan_and_quote_details.sql](src/main/resources/db/migration/V3__review_plan_and_quote_details.sql) | `final_plan`, `issue_date`, `details` 추가 |
| [V4__quote_revision_and_task_outcome.sql](src/main/resources/db/migration/V4__quote_revision_and_task_outcome.sql) | 견적 revision·수정 요청과 작업 종료 결과 |
| [V5__analysis_outbox.sql](src/main/resources/db/migration/V5__analysis_outbox.sql) | RabbitMQ 발행 복구용 outbox |

## 코드 구조

```
com.skala.supplyquote.supplyquote_backend
├─ auth/           로그인·JWT 발급·현재 사용자·회사별 소유권 검사
├─ web/            화면용 API 컨트롤러 + dto (고객용/담당자용 견적 응답을 클래스로 분리)
├─ internal/       내부 업무 API 컨트롤러 + dto (business MCP / Agent 호출)
├─ service/        RFQ·검토·견적·작업 상태·SSE·Agent HTTP 구현
├─ dto/            화면용·내부용이 함께 쓰는 구조 (ReviewParts, QuoteParts: 정답지 JSON과 같은 필드)
├─ messaging/      RabbitMQ 규약·outbox 발행·소비·DLQ
├─ domain/         상태 enum (엔티티는 구현 단계에서 추가)
├─ common/         공통 오류 응답, 오류 코드, 예외 처리
└─ config/         OpenAPI, JWT 보안, 내부 API 키, DemoDataLoader(demo 프로파일)
```

## 규칙

- DB 스키마는 Flyway(`db/migration`)로만 바꾼다. 이미 푸시된 `V*.sql`은 수정하지 않고 새 파일을 추가한다.
- 카탈로그·재고·결과 테이블은 **데이터셋의 컬럼 정의를 그대로** 따른다 (`model_id`, `option_code` 등). 바꿀 때는 Agent 팀·데이터셋 담당과 먼저 맞춘다.
- 업무 데이터 쓰기는 Spring이 담당한다. 새 문서의 임베딩·벡터 적재는 후속 단계이며 Agent 팀의 RAG 서비스에 위임할 예정이다. 적재 API·DB 권한은 [Agent 연동 계약](docs/AGENT.md#rag-검색과-후속-단계)의 미확정 항목이다.
- `/internal/**`는 `X-Internal-Api-Key: ${INTERNAL_API_KEY}`를 요구한다. Spring → Agent HTTP에도 같은 헤더를 보낸다. 키가 비어 있으면 내부 API 요청을 거부한다. 배포 네트워크 접근 제한도 별도로 설정한다.
- 데모 데이터는 Flyway가 아니라 `DemoDataLoader`가 demo 프로파일에서만 적재한다.
