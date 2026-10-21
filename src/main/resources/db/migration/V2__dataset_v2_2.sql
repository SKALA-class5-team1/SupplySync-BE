-- =====================================================================
-- RFQ Agent DB v2.1 최종 (대성오토텍)
-- PostgreSQL 15+ / pgvector 0.5+
-- 묶음: 사용자·인증 / 제품(SG1) / 부품·재고(SG2) / Agent 결과 / VectorDB
-- 쓰기는 모두 Spring 내부 API를 거친다 (MCP는 DB에 직접 쓰지 않음)
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS vector;
-- Flyway는 기본 스키마(public)만 검색 경로로 설정할 수 있다.
-- Supabase의 vector 확장은 extensions에 있으므로 이 마이그레이션 안에서 함께 찾는다.
SET LOCAL search_path = public, extensions;

-- ---------------------------------------------------------------------
-- 사용자·인증
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 제품 (SG1)
-- ---------------------------------------------------------------------
CREATE TABLE models (
    model_id        VARCHAR(20)  PRIMARY KEY,               -- EWP-12-A / B / C
    model_name      VARCHAR(50)  NOT NULL,
    flow_lpm        NUMERIC(5,1) NOT NULL,                  -- 유량 L/min
    head_kpa        INT          NOT NULL,                  -- 압력 kPa
    voltage_min     NUMERIC(4,1) NOT NULL,
    voltage_max     NUMERIC(4,1) NOT NULL,
    temp_min        INT          NOT NULL,                  -- ℃
    temp_max        INT          NOT NULL,                  -- ℃
    ip_rating       VARCHAR(10)  NOT NULL,                  -- IP67 < IP6K9K
    noise_db        INT          NOT NULL,
    comm            VARCHAR(20)  NOT NULL,                  -- 'LIN,PWM'
    size_l_mm       INT          NOT NULL,
    size_w_mm       INT          NOT NULL,
    size_h_mm       INT          NOT NULL,
    weight_kg       NUMERIC(4,2) NOT NULL,
    base_price_krw  INT          NOT NULL                   -- 기본 판매 단가
);

-- ---------------------------------------------------------------------
-- 부품·재고 (SG2)  ※ options가 parts를 참조하므로 parts를 먼저 생성
-- ---------------------------------------------------------------------
CREATE TABLE parts (
    part_id         VARCHAR(20) PRIMARY KEY,
    part_name       VARCHAR(50) NOT NULL,
    supplier        VARCHAR(50) NOT NULL,
    unit_cost_krw   INT         NOT NULL,                   -- 부품 비용 (개당 원가)
    lead_time_days  INT         NOT NULL,                   -- 신규 발주 조달기간
    is_critical     BOOLEAN     NOT NULL                    -- 부족하면 생산 불가
);

CREATE TABLE options (
    option_code       VARCHAR(20)  PRIMARY KEY,             -- OPT-HT / OPT-IP9K
    option_name       VARCHAR(50)  NOT NULL,
    changes_field     VARCHAR(20)  NOT NULL,                -- 바뀌는 models 컬럼
    new_value         VARCHAR(20)  NOT NULL,
    add_weight_kg     NUMERIC(4,2) NOT NULL DEFAULT 0,
    add_noise_db      INT          NOT NULL DEFAULT 0,
    add_price_krw     INT          NOT NULL,                -- 옵션 추가 판매 단가
    replaces_part_id  VARCHAR(20)  REFERENCES parts(part_id), -- BOM에서 빠지는 부품
    uses_part_id      VARCHAR(20)  REFERENCES parts(part_id)  -- BOM에 들어가는 부품
);

CREATE TABLE model_options (
    model_id     VARCHAR(20) REFERENCES models(model_id),
    option_code  VARCHAR(20) REFERENCES options(option_code),
    PRIMARY KEY (model_id, option_code)
);

CREATE TABLE bom (
    model_id      VARCHAR(20) REFERENCES models(model_id),
    part_id       VARCHAR(20) REFERENCES parts(part_id),
    qty_per_unit  INT NOT NULL CHECK (qty_per_unit > 0),
    PRIMARY KEY (model_id, part_id)
);

CREATE TABLE inventory (
    part_id        VARCHAR(20) PRIMARY KEY REFERENCES parts(part_id),
    on_hand        INT  NOT NULL CHECK (on_hand >= 0),      -- 창고 실물
    allocated      INT  NOT NULL CHECK (allocated >= 0),    -- 기존 주문 배정분
    incoming_qty   INT  NOT NULL DEFAULT 0,                 -- 다음 입고 예정 수량
    incoming_date  DATE                                     -- 다음 입고 예정일
    -- 가용 = on_hand - allocated (저장하지 않고 계산)
);

CREATE TABLE part_substitutes (
    part_id             VARCHAR(20) REFERENCES parts(part_id),   -- 원래 부품
    substitute_part_id  VARCHAR(20) REFERENCES parts(part_id),   -- 대체 부품
    validation_days     INT     NOT NULL,                        -- 적용 전 검증기간
    pin_compatible      BOOLEAN NOT NULL,                        -- 회로 변경 불필요 여부
    ref_chunk_id        VARCHAR(30),                             -- 근거 청크 (논리 연결)
    PRIMARY KEY (part_id, substitute_part_id),
    CHECK (part_id <> substitute_part_id)
    -- 추가 원가는 저장하지 않음: parts.unit_cost_krw 차이로 계산
);

-- ---------------------------------------------------------------------
-- Agent 결과
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- VectorDB (rag MCP) — bge-m3 dense, 1024차원
-- ---------------------------------------------------------------------
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

-- 검색 예시 (rag MCP)
-- SELECT chunk_id, section, content, 1 - (embedding <=> :query_vec) AS score
--   FROM doc_chunks
--  WHERE embedding IS NOT NULL AND doc_type = :doc_type
--  ORDER BY embedding <=> :query_vec
--  LIMIT 3;

-- ---------------------------------------------------------------------
-- SupplySync 백엔드 운영 테이블 (데이터셋의 13개 업무 테이블 외)
-- ---------------------------------------------------------------------
CREATE TABLE ai_tasks (
    task_id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    rfq_number     VARCHAR(30)  NOT NULL REFERENCES rfqs (rfq_number),
    requested_by   BIGINT       NOT NULL REFERENCES users (user_id),
    status         VARCHAR(30)  NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'NEEDS_CLARIFICATION', 'WAITING_APPROVAL', 'REVALIDATING',
                          'REGISTERING', 'ON_HOLD', 'DONE', 'FAILED')),
    current_step   VARCHAR(30),
    thread_id      VARCHAR(64)  NOT NULL UNIQUE,
    request_id     UUID         NOT NULL UNIQUE,
    failure_reason TEXT,
    trace_url      TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ
);
CREATE INDEX idx_ai_tasks_rfq ON ai_tasks (rfq_number, created_at DESC);
CREATE INDEX idx_ai_tasks_status ON ai_tasks (status);

CREATE TABLE quote_daily_seq (
    day      DATE PRIMARY KEY,
    last_seq INT  NOT NULL CHECK (last_seq > 0)
);

CREATE TABLE processed_messages (
    message_id   UUID         NOT NULL,
    consumer     VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, consumer)
);

-- RAG MCP 계정이 먼저 존재할 때 문서 청크 읽기만 허용한다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rag_reader') THEN
        GRANT SELECT ON doc_chunks TO rag_reader;
    END IF;
END
$$;
