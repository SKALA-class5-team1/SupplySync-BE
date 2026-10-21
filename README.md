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
- `/internal/**`은 기존 `X-Internal-Api-Key`로 인증한다. Agent에 사용자 JWT를 보내지 않는다. 프론트 연동 계약은 [인증 연동 안내](docs/FRONTEND_AUTH_HANDOFF.md)를 참고한다.
- 오류 응답은 `{code, message, details, timestamp}` 하나로 통일한다.

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
| [docs/FRONTEND_AUTH_HANDOFF.md](docs/FRONTEND_AUTH_HANDOFF.md) | JWT 로그인·권한·SSE 프론트 연동 |
| [docs/DESIGN.md](docs/DESIGN.md) | **백엔드 설계서 v2**: 데이터셋 반영, 해야 할 일, MVC 설계, 다이어그램 15종 |
| [docs/diagrams/](docs/diagrams/) | 다이어그램 원본(`.mmd`)과 PNG |
| [docs/ERD.md](docs/ERD.md) | v2 데이터셋 스키마와 v3~v5 후속 확장 |
| [docs/MESSAGING.md](docs/MESSAGING.md) | RabbitMQ 메시지 규약 |
| [docs/RAG_MCP_INTEGRATION.md](docs/RAG_MCP_INTEGRATION.md) | Supabase·RAG MCP·RabbitMQ 연결 및 담당 경계 |
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
- 업무 데이터 쓰기는 Spring이 담당한다. 새 문서의 임베딩·벡터 적재는 Agent 팀의 RAG 서비스에 위임할 예정이며, Spring은 문서 수신과 전달 경계를 담당한다. 적재 API·DB 권한은 [연동 구조](docs/RAG_MCP_INTEGRATION.md)에서 확정 항목으로 관리한다.
- `/internal/**`는 `X-Internal-Api-Key: ${INTERNAL_API_KEY}`를 요구한다. Spring → Agent HTTP에도 같은 헤더를 보낸다. 키가 비어 있으면 내부 API 요청을 거부한다. 배포 네트워크 접근 제한도 별도로 설정한다.
- 데모 데이터는 Flyway가 아니라 `DemoDataLoader`가 demo 프로파일에서만 적재한다.
