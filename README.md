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

# 3. 앱 실행 (시작할 때 Flyway가 테이블을 만든다)
./gradlew bootRun

# 3-1. 데모 데이터까지 넣고 실행 (RFQ HB-EV7-EWP-001 시나리오)
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

| 확인할 것 | 주소 |
| --- | --- |
| Swagger UI | http://localhost:8080/swagger-ui.html (상단 드롭다운: 화면용 / 내부 업무) |
| OpenAPI JSON | http://localhost:8080/v3/api-docs/1-web, `/v3/api-docs/2-internal` |
| 헬스체크 | http://localhost:8080/actuator/health |
| RabbitMQ 관리 콘솔 | http://localhost:15672 (`RABBITMQ_USER` / `RABBITMQ_PASSWORD`) |

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
- `V1__init_schema.sql`처럼 이미 반영된 `V*.sql`을 고치면 Flyway가 체크섬 불일치 오류를 낸다. 새 `V*.sql`을 추가한다.

## 문서

| 문서 | 내용 |
| --- | --- |
| Swagger UI | 화면용 API(`/api/**`), 내부 업무 API(`/internal/**`) 계약. `[STUB]`는 고정 응답 |
| [docs/ERD.md](docs/ERD.md) | 테이블 구조와 설계 결정 |
| [docs/MESSAGING.md](docs/MESSAGING.md) | RabbitMQ 메시지 규약 |
| [V1__init_schema.sql](src/main/resources/db/migration/V1__init_schema.sql) | DB 스키마 원본 |
| [demo_data.sql](src/main/resources/db/seed/demo_data.sql) | 데모 데이터 (지영님 데이터셋 기반, demo 프로파일) |

## 코드 구조

```
com.skala.supplyquote.supplyquote_backend
├─ web/            화면용 API 컨트롤러 + dto (+ StubData: 스텁 응답 샘플)
├─ internal/       내부 업무 API 컨트롤러 + dto (business MCP / Agent 호출)
├─ messaging/      RabbitMQ 규약 (contract: 이름 상수, 메시지 봉투, payload)
├─ domain/         상태 enum (엔티티는 구현 단계에서 추가)
├─ common/         공통 오류 응답, 예외 처리
└─ config/         OpenAPI, DemoDataLoader(demo 프로파일) 등 설정
```

## 규칙

- DB 스키마는 Flyway(`db/migration`)로만 바꾼다. 이미 반영된 `V*.sql`은 수정하지 않고 새 파일을 추가한다.
- 카탈로그·재고 테이블은 지영님 데이터셋의 컬럼명을 따른다 (`model_id`, `option_code` 등). 바꿀 때는 Agent 팀과 먼저 맞춘다.
- `/internal/**`는 docker network 안에서만 열고 `X-Internal-Api-Key`로 보호한다 (인증 단계에서 적용).
- 데모 데이터는 Flyway가 아니라 `DemoDataLoader`가 demo 프로파일에서만 적재한다. models 테이블에 데이터가 있으면 건너뛴다.
