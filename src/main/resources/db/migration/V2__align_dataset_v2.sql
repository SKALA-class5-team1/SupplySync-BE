-- V2: 데이터셋 v2.1/v2.2 구조에 맞춘다.
--
-- 배경: V1(푸시 완료)의 업무 테이블은 이전 데이터셋 기준으로 설계되어 있었다. 데이터셋 v2.1부터
--       companies/users/rfqs/rfq_reviews/quotes/doc_chunks 구조가 바뀌어서 V2로 정렬한다.
-- 원칙: 아래 [데이터셋] 블록은 개인폴더/Input/rfq_dataset_v2.2/02_postgres/01_schema.sql의 정의를 수정 없이 옮긴 것이다.
--       (스크립트로 추출해서 조립했다. 타입은 데이터셋을 따라 TIMESTAMP를 쓴다.)
-- 주의: V1 시점에 DemoDataLoader가 적재한 데모 데이터는 삭제된다 (카탈로그 포함). 데모 데이터는 새 시드로 다시 적재한다.
--       V1에는 데모 외의 데이터 입력 경로가 없었으므로 잃는 업무 데이터는 없다.

-- ============================================================
-- 1. V1의 업무 테이블 삭제 (구조가 바뀌었거나 데이터셋 구조로 대체됨)
-- ============================================================
DROP TABLE IF EXISTS registration_requests CASCADE;   -- quotes의 조건부 UPDATE로 멱등을 보장한다
DROP TABLE IF EXISTS purchase_request_items CASCADE;  -- 구매요청은 데이터셋에 없다 (범위 결정 후 재검토)
DROP TABLE IF EXISTS purchase_requests CASCADE;
DROP TABLE IF EXISTS quote_items CASCADE;
DROP TABLE IF EXISTS approvals CASCADE;               -- quotes.approved_by / reject_reason이 대체한다
DROP TABLE IF EXISTS quotes CASCADE;
DROP TABLE IF EXISTS analysis_results CASCADE;        -- rfq_reviews가 대체한다
DROP TABLE IF EXISTS ai_tasks CASCADE;                -- 아래에서 새 구조로 재생성
DROP TABLE IF EXISTS rfq_reviews CASCADE;
DROP TABLE IF EXISTS rfqs CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS doc_chunks CASCADE;              -- 1536차원 → 1024차원(bge-m3)

-- 카탈로그·재고는 구조가 같지만 V1의 데모 데이터(이전 데이터셋 값)를 비운다. 새 시드가 다시 채운다.
TRUNCATE TABLE part_substitutes, inventory, bom, model_options, options, parts, models CASCADE;

-- ============================================================
-- 2. [데이터셋] 사용자·인증
-- ============================================================
CREATE TABLE companies (
    company_id    BIGSERIAL PRIMARY KEY,
    name          VARCHAR(50) NOT NULL UNIQUE,              -- 한별모터스 / 대성오토텍
    company_type  VARCHAR(20) NOT NULL
                  CHECK (company_type IN ('CUSTOMER', 'SUPPLIER'))
);

CREATE TABLE users (
    user_id        BIGSERIAL PRIMARY KEY,
    login_id       VARCHAR(50)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,                   -- BCrypt 해시, 원문 저장 금지
    company_id     BIGINT       NOT NULL REFERENCES companies(company_id),
    name           VARCHAR(50)  NOT NULL,                   -- 담당자명 (승인자 표시)
    user_type      VARCHAR(20)  NOT NULL
                   CHECK (user_type IN ('CUSTOMER', 'MANAGER')),
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT now()
    -- 규칙(애플리케이션에서 검증): CUSTOMER는 CUSTOMER 회사, MANAGER는 SUPPLIER 회사 소속
);

-- ============================================================
-- 3. 기존 테이블 보정 (구조는 같고 일부 컬럼·제약만 다르다)
-- ============================================================
-- part_substitutes: compatibility_note, requires_spec_revalidation → pin_compatible, ref_chunk_id
ALTER TABLE part_substitutes DROP COLUMN IF EXISTS compatibility_note;
ALTER TABLE part_substitutes DROP COLUMN IF EXISTS requires_spec_revalidation;
ALTER TABLE part_substitutes ADD COLUMN pin_compatible BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE part_substitutes ALTER COLUMN pin_compatible DROP DEFAULT;
ALTER TABLE part_substitutes ADD COLUMN ref_chunk_id VARCHAR(30);
-- V1이 붙였던 기본값 제거 (데이터셋은 검증 기간을 반드시 지정한다)
ALTER TABLE part_substitutes ALTER COLUMN validation_days DROP DEFAULT;

-- bom: 수량 CHECK 추가 (테이블을 비웠으므로 기존 행이 없다)
ALTER TABLE bom ADD CONSTRAINT bom_qty_per_unit_check CHECK (qty_per_unit > 0);

-- inventory: 재고 수량 CHECK 추가, 입고 예정 수량 기본값 0
ALTER TABLE inventory ADD CONSTRAINT inventory_on_hand_check CHECK (on_hand >= 0);
ALTER TABLE inventory ADD CONSTRAINT inventory_allocated_check CHECK (allocated >= 0);
ALTER TABLE inventory ALTER COLUMN incoming_qty SET DEFAULT 0;

-- ============================================================
-- 4. [데이터셋] Agent 결과: rfqs, rfq_reviews, quotes
-- ============================================================
CREATE TABLE rfqs (
    rfq_id               BIGSERIAL PRIMARY KEY,
    rfq_number           VARCHAR(30) NOT NULL UNIQUE,      -- HB-EV7-EWP-001
    customer_company_id  BIGINT NOT NULL REFERENCES companies(company_id),
    uploaded_by          BIGINT REFERENCES users(user_id),
    item                 VARCHAR(100),
    file_path            VARCHAR(255),
    status               VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
                         CHECK (status IN ('RECEIVED', 'IN_REVIEW', 'QUOTED')),
    received_at          TIMESTAMP NOT NULL DEFAULT now(),
    -- 아래는 SG1이 RFQ를 읽은 뒤 채움 (추출 전 NULL)
    eau                  INT,                               -- 연간 물량
    sample_qty           INT,                               -- 시작품 수량
    sample_due_date      DATE,                              -- 고객 납기 (시작품)
    sop_date             DATE,                              -- 양산 시작
    target_price_krw     INT,                               -- 목표 단가
    quote_due_date       DATE                               -- 견적 회신 마감
);

CREATE TABLE rfq_reviews (
    review_id           BIGSERIAL PRIMARY KEY,
    rfq_number          VARCHAR(30) NOT NULL,               -- rfqs.rfq_number와 문자열 연결 (FK 없음)
    thread_id           VARCHAR(64) NOT NULL,               -- LangGraph 실행 ID
    status              VARCHAR(20) NOT NULL DEFAULT 'RUNNING'
                        CHECK (status IN ('RUNNING', 'WAITING', 'DONE')),
    -- SG1
    selected_model      VARCHAR(20) REFERENCES models(model_id),
    options             TEXT[],
    fit_type            VARCHAR(20),                        -- 표준 / 파생 사양 / 신규 개발
    issues              JSONB,
    passed_checks       JSONB,
    assumptions         JSONB,
    confirmed_by        BIGINT REFERENCES users(user_id),   -- 가정값 승인자
    confirmed_at        TIMESTAMP,
    customer_questions  JSONB,
    contract_risks      JSONB,
    -- SG2
    shortages           JSONB,
    alternatives        JSONB,
    recommended_alt     VARCHAR(30),                        -- WAIT_INCOMING / PARTIAL_DELIVERY / SUBSTITUTE_PART / NEW_ORDER
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (rfq_number, thread_id)                          -- 같은 실행은 한 건만 (upsert 기준)
);

CREATE TABLE quotes (
    quote_id         VARCHAR(30) PRIMARY KEY,               -- Q-2026-1019-001
    review_id        BIGINT NOT NULL REFERENCES rfq_reviews(review_id),
    rfq_number       VARCHAR(30) NOT NULL,
    model_id         VARCHAR(20) REFERENCES models(model_id),
    options          TEXT[],
    unit_price_krw   INT NOT NULL,                          -- 고객 공개
    parts_cost_krw   INT,                                   -- 내부용
    margin_pct       NUMERIC(4,1),                          -- 부품 원가 기준, 내부용
    price_by_year    JSONB,
    sample_qty       INT,
    sample_due_date  DATE,                                  -- 약속한 납기 (rfqs 값 복사)
    valid_until      DATE,                                  -- 발행일 + 30일
    conditions       JSONB,
    checks           JSONB,                                 -- 내부용
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                     CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED', 'REGISTERED')),
    approved_by      BIGINT REFERENCES users(user_id),
    approved_at      TIMESTAMP,
    reject_reason    TEXT,
    registered_at    TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

-- ============================================================
-- 5. [데이터셋] VectorDB: doc_chunks (bge-m3 dense, 1024차원)
-- ============================================================
CREATE TABLE doc_chunks (
    chunk_id          VARCHAR(30) PRIMARY KEY,              -- dsn-003, pol-003 ...
    doc_type          VARCHAR(20) NOT NULL
                      CHECK (doc_type IN ('design_std', 'catalog', 'policy', 'alt_parts')),
    source            VARCHAR(50) NOT NULL,                 -- 원본 파일명
    section           VARCHAR(50) NOT NULL,                 -- 근거 인용용 섹션 제목
    content           TEXT        NOT NULL,
    embedding         vector(1024),                         -- 정규화 후 저장, 코사인 거리
    embedding_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (embedding_status IN ('PENDING', 'DONE', 'FAILED')),
    embedding_model   VARCHAR(50),                          -- BAAI/bge-m3
    embedded_at       TIMESTAMP,
    content_hash      VARCHAR(64) NOT NULL                  -- SHA-256, 바뀌면 재임베딩
);

CREATE INDEX idx_doc_chunks_type ON doc_chunks (doc_type);
CREATE INDEX idx_doc_chunks_embedding ON doc_chunks
    USING hnsw (embedding vector_cosine_ops);

-- rag MCP 읽기 전용 계정(init 스크립트가 만들어 둠)에 doc_chunks 조회만 허용한다. 업무 테이블은 보지 못한다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rag_reader') THEN
        GRANT SELECT ON doc_chunks TO rag_reader;
    END IF;
END
$$;

-- ============================================================
-- 6. [백엔드] 비동기 작업 추적, 견적 번호
-- ============================================================
-- Agent 실행 1회 = 작업 1건. rfq_reviews(rfq_number, thread_id)와 thread_id 문자열로 연결한다.
-- 데이터셋의 rfq_reviews.status(RUNNING/WAITING/DONE)보다 화면에 필요한 세분화된 상태와 진행 단계를 가진다.
CREATE TABLE ai_tasks (
    task_id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    rfq_number     VARCHAR(30)  NOT NULL REFERENCES rfqs (rfq_number),
    requested_by   BIGINT       NOT NULL REFERENCES users (user_id),
    status         VARCHAR(30)  NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'NEEDS_CLARIFICATION', 'WAITING_APPROVAL', 'REVALIDATING',
                          'REGISTERING', 'ON_HOLD', 'DONE', 'FAILED')),
    current_step   VARCHAR(30),
    thread_id      VARCHAR(64)  NOT NULL UNIQUE,
    -- 분석 요청 메시지의 멱등 키. 같은 요청이 두 번 들어와도 작업은 한 건만 만든다
    request_id     UUID         NOT NULL UNIQUE,
    failure_reason TEXT,
    trace_url      TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ
);
CREATE INDEX idx_ai_tasks_rfq ON ai_tasks (rfq_number, created_at DESC);
CREATE INDEX idx_ai_tasks_status ON ai_tasks (status);

-- quote_id(Q-YYYY-MMDD-NNN)의 일별 번호. INSERT ... ON CONFLICT DO UPDATE ... RETURNING으로 원자적으로 증가시킨다.
CREATE TABLE quote_daily_seq (
    day      DATE PRIMARY KEY,
    last_seq INT  NOT NULL CHECK (last_seq > 0)
);
