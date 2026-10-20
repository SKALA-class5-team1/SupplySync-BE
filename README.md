# SupplySync-BE

공급망 연계 RFQ·재고·구매 대응 Agent의 Spring Boot 백엔드.
Java 21, Spring Boot 4.1.1, PostgreSQL 16 + pgvector, RabbitMQ 3.13, Flyway, springdoc(Swagger).

## 처음 실행하기

```bash
# 1. 환경 변수 파일 만들기 (비밀번호를 바꾼다. .env는 커밋하지 않는다)
cp .env.example .env

# 2. PostgreSQL(pgvector) + RabbitMQ 실행
docker compose up -d
docker compose ps            # 둘 다 healthy가 될 때까지 기다린다

# 3. 앱 실행 (시작할 때 Flyway가 V1, V2를 적용한다)
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

> **기존 DB(V1)를 쓰고 있다면:** 다음 `bootRun` 때 Flyway가 V2를 자동 적용한다. V2는 V1 시절의 데모 데이터와 업무 테이블을 지우고
> 데이터셋 v2.2 구조로 바꾼다 (`processed_messages`는 유지). 데모 데이터는 `demo` 프로파일로 다시 적재한다.
> 처음부터 깨끗하게 시작하려면 `docker compose down -v` 후 다시 띄운다.

## API 규칙

- **JSON 필드명은 snake_case**이다 (`rfq_number`, `unit_price_krw`). 데이터셋 정답지(`05_expected`)와 같은 이름이라 Agent·MCP·프론트가 같은 이름을 쓴다.
  Swagger 문서의 필드명도 같다 (테스트가 런타임과 문서의 일치를 확인한다).
- 현재 모든 REST API는 **스텁**이다 (`[STUB]` 표시). 응답은 데이터셋 정답지(`src/main/resources/stub/*.json`)로 만든다.
- 오류 응답은 `{code, message, details, timestamp}` 하나로 통일한다.

## 데모 데이터 (demo 프로파일)

시드는 데이터셋 v2.2의 `02_seed.sql`, `03_doc_chunks.sql`을 **수정 없이 복사**한 것이다 (`src/main/resources/db/seed/`). 데이터셋이 바뀌면 파일을 교체한다.

| 계정 | 소속 | 역할 | 비밀번호 |
| --- | --- | --- | --- |
| `hb_buyer01` | 한별모터스 | CUSTOMER | `demo1234` |
| `ds_sales01` | 대성오토텍 | MANAGER | `demo1234` |

- 시드는 `models` 테이블이 비어 있을 때만 적재한다 (재기동해도 중복되지 않는다).
- RFQ 원문(`01_rfq/RFQ_HB-EV7-EWP-001.md`)은 `RFQ_FILES_DIR`(기본 `./rfq-files`) 아래로 복사된다. 시드의 `rfqs.file_path`는 이 디렉터리 기준 상대 경로이다.
- `doc_chunks` 26개는 임베딩이 비어 있다(`embedding_status = PENDING`). AI 서버 `/embeddings`(bge-m3, 1024차원)로 채우는 파이프라인은 아직 없다.

## 인프라 접속 정보

| 항목 | 값 |
| --- | --- |
| PostgreSQL | `localhost:5432` (`DB_PORT`로 변경), DB `supplysync`, 이미지 `pgvector/pgvector:pg16` |
| 앱 계정 | `DB_USER` (기본 `supplysync_app`). DB 소유자이며 superuser가 아님 |
| 관리자 계정 | `POSTGRES_ADMIN_USER` (기본 `postgres`). 앱에서는 쓰지 않음 |
| rag MCP 계정 | `RAG_DB_USER` (기본 `rag_reader`). `doc_chunks` 조회만 가능. 비워 두면 만들지 않음 |
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
| Swagger UI | 화면용 API(`/api/**`), 내부 업무 API(`/internal/**`) 계약. `[STUB]`는 고정 응답 |
| [docs/DESIGN.md](docs/DESIGN.md) | **백엔드 설계서 v2**: 데이터셋 반영, 해야 할 일, MVC 설계, 다이어그램 15종 |
| [docs/diagrams/](docs/diagrams/) | 다이어그램 원본(`.mmd`)과 PNG |
| [docs/ERD.md](docs/ERD.md) | 현재 스키마 (V1 + V2, 데이터셋 v2.2와 동일) |
| [docs/MESSAGING.md](docs/MESSAGING.md) | RabbitMQ 메시지 규약 |
| [V1__init_schema.sql](src/main/resources/db/migration/V1__init_schema.sql), [V2__align_dataset_v2.sql](src/main/resources/db/migration/V2__align_dataset_v2.sql) | DB 스키마 원본 |

## 코드 구조

```
com.skala.supplyquote.supplyquote_backend
├─ auth/           로그인 컨트롤러 (스텁)
├─ web/            화면용 API 컨트롤러 + dto (고객용/담당자용 견적 응답을 클래스로 분리)
├─ internal/       내부 업무 API 컨트롤러 + dto (business MCP / Agent 호출)
├─ dto/            화면용·내부용이 함께 쓰는 구조 (ReviewParts, QuoteParts: 정답지 JSON과 같은 필드)
├─ messaging/      RabbitMQ 규약 (contract: 이름 상수, 메시지 봉투, payload)
├─ domain/         상태 enum (엔티티는 구현 단계에서 추가)
├─ common/         공통 오류 응답, 오류 코드, 예외 처리
├─ config/         OpenAPI, DemoDataLoader(demo 프로파일)
└─ stub/           스텁 응답 재료 (정답지 JSON을 DTO로 읽는다. 구현되면 삭제)
```

## 규칙

- DB 스키마는 Flyway(`db/migration`)로만 바꾼다. 이미 푸시된 `V*.sql`은 수정하지 않고 새 파일을 추가한다.
- 카탈로그·재고·결과 테이블은 **데이터셋의 컬럼 정의를 그대로** 따른다 (`model_id`, `option_code` 등). 바꿀 때는 Agent 팀·데이터셋 담당과 먼저 맞춘다.
- 모든 쓰기는 `/internal/**`(Spring)를 거친다. MCP·Agent는 DB에 직접 쓰지 않는다.
- `/internal/**`는 docker network 안에서만 열고 `X-Internal-Api-Key`로 보호한다 (인증 단계에서 적용).
- 데모 데이터는 Flyway가 아니라 `DemoDataLoader`가 demo 프로파일에서만 적재한다.
