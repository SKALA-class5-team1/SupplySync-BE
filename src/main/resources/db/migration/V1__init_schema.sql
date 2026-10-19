-- SupplySync 초기 스키마 (PostgreSQL 16 + pgvector)
-- 카탈로그·재고·문서 청크(models ~ doc_chunks)는 지영님 데이터셋(Input/rfq_dataset/02_postgres/01_schema.sql)을
-- 컬럼명과 타입 그대로 따른다. Agent 규칙 파일과 정답 JSON이 이 컬럼명(model_id, option_code, flow_lpm ...)을 쓰기 때문이다.
-- 데이터셋 대비 변경점은 아래 [변경] 주석으로 표시했다.
-- 규칙: 상태 값은 PG enum 대신 VARCHAR + CHECK. 시각은 TIMESTAMPTZ(UTC). 푸시된 V파일은 수정하지 않고 새 V파일을 추가한다.

-- pgvector. 일반 계정은 확장을 만들 수 없으므로 docker/postgres/init 스크립트가 먼저 만들어 둔다.
-- 이미 있으면 IF NOT EXISTS가 권한 검사 없이 건너뛴다.
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 1. 사용자
-- ============================================================
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    company       VARCHAR(200),
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'MANAGER')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============================================================
-- 2. 카탈로그 (business MCP → /internal/**)
-- ============================================================

-- 표준 모델 사양 (우리가 만들 수 있는 제품)
CREATE TABLE models (
    model_id        VARCHAR(20) PRIMARY KEY,
    model_name      VARCHAR(50)  NOT NULL,
    flow_lpm        NUMERIC(5,1) NOT NULL,   -- 유량 (L/min)
    head_kpa        INT          NOT NULL,   -- 위 유량을 내는 수두 기준 (kPa)
    voltage_min     NUMERIC(4,1) NOT NULL,
    voltage_max     NUMERIC(4,1) NOT NULL,
    temp_min        INT          NOT NULL,   -- ℃
    temp_max        INT          NOT NULL,   -- ℃
    ip_rating       VARCHAR(10)  NOT NULL,   -- IP67 < IP6K9K
    noise_db        INT          NOT NULL,   -- dB(A)
    comm            VARCHAR(20)  NOT NULL,   -- 지원 통신 (예: 'LIN,PWM')
    size_l_mm       INT          NOT NULL,
    size_w_mm       INT          NOT NULL,
    size_h_mm       INT          NOT NULL,
    weight_kg       NUMERIC(4,2) NOT NULL,
    base_price_krw  INT          NOT NULL    -- 기본 판매 단가
);

-- 부품 마스터
CREATE TABLE parts (
    part_id         VARCHAR(20) PRIMARY KEY,
    part_name       VARCHAR(50) NOT NULL,
    supplier        VARCHAR(50) NOT NULL,
    unit_cost_krw   INT         NOT NULL,
    lead_time_days  INT         NOT NULL,     -- 신규 발주 시 입고까지 걸리는 일수
    is_critical     BOOLEAN     NOT NULL      -- 부족하면 생산 불가한 핵심 부품
);

-- 옵션 (표준 모델에 붙여 사양을 바꾸는 것)
-- [변경] replaces_part_id, uses_part_id에 parts FK 추가 (데이터셋은 FK 없음). 시드는 parts를 먼저 넣는다.
CREATE TABLE options (
    option_code        VARCHAR(20) PRIMARY KEY,
    option_name        VARCHAR(50) NOT NULL,
    changes_field      VARCHAR(20) NOT NULL,  -- 바뀌는 사양 항목 (models 컬럼명)
    new_value          VARCHAR(20) NOT NULL,  -- 바뀐 값
    add_weight_kg      NUMERIC(4,2) NOT NULL DEFAULT 0,
    add_noise_db       INT          NOT NULL DEFAULT 0,
    add_price_krw      INT          NOT NULL,
    replaces_part_id   VARCHAR(20) REFERENCES parts (part_id),  -- BOM에서 빠지는 부품
    uses_part_id       VARCHAR(20) REFERENCES parts (part_id)   -- BOM에 들어가는 부품
);

-- 어떤 모델에 어떤 옵션을 붙일 수 있는지
CREATE TABLE model_options (
    model_id     VARCHAR(20) REFERENCES models (model_id),
    option_code  VARCHAR(20) REFERENCES options (option_code),
    PRIMARY KEY (model_id, option_code)
);

-- 부품 구성표 (모델 1개를 만드는 데 필요한 부품, 옵션 적용 전 기준)
CREATE TABLE bom (
    model_id      VARCHAR(20) REFERENCES models (model_id),
    part_id       VARCHAR(20) REFERENCES parts (part_id),
    qty_per_unit  INT NOT NULL,
    PRIMARY KEY (model_id, part_id)
);

-- 재고. 가용 재고 = on_hand - allocated (저장하지 않고 계산한다)
CREATE TABLE inventory (
    part_id        VARCHAR(20) PRIMARY KEY REFERENCES parts (part_id),
    on_hand        INT  NOT NULL,   -- 창고 실물 재고
    allocated      INT  NOT NULL,   -- 기존 주문에 이미 배정된 수량
    incoming_qty   INT  NOT NULL,   -- 입고 예정 수량
    incoming_date  DATE             -- 입고 예정일
);

-- [신규] 대체 부품. 데이터셋에는 alt_parts_criteria.md 텍스트로만 있어 테이블로 만들었다. (지영님 확인 필요)
-- 추가 원가는 parts.unit_cost_krw 차이로 계산한다.
CREATE TABLE part_substitutes (
    part_id                    VARCHAR(20) NOT NULL REFERENCES parts (part_id),
    substitute_part_id         VARCHAR(20) NOT NULL REFERENCES parts (part_id),
    compatibility_note         TEXT,
    validation_days            INT         NOT NULL DEFAULT 0,   -- 적용 전 검증 시험 기간
    requires_spec_revalidation BOOLEAN     NOT NULL DEFAULT TRUE, -- 선택하면 사양을 다시 검증해야 한다
    PRIMARY KEY (part_id, substitute_part_id),
    CHECK (part_id <> substitute_part_id)
);

-- SG1 검토 결과 기록. rfq_id는 요청서의 RFQ 번호(예: HB-EV7-EWP-001)이며 rfqs.rfq_number와 문자열로 연결한다.
CREATE TABLE rfq_reviews (
    review_id       SERIAL PRIMARY KEY,
    rfq_id          VARCHAR(30) NOT NULL,
    selected_model  VARCHAR(20),
    options         TEXT[],
    fit_type        VARCHAR(20),   -- '표준' / '파생 사양' / '신규 개발'
    issues          JSONB,
    reviewed_at     TIMESTAMP DEFAULT now()
);

-- ============================================================
-- 3. 문서 청크 (rag MCP). 같은 DB의 pgvector를 쓴다.
-- 임베딩 차원은 사용하는 임베딩 모델에 맞춘다 (현재 1536 가정). 임베딩은 적재 작업이 채운다.
-- ============================================================
CREATE TABLE doc_chunks (
    chunk_id   VARCHAR(30) PRIMARY KEY,
    doc_type   VARCHAR(20) NOT NULL,   -- design_std / catalog / policy / alt_parts / rfq
    source     VARCHAR(50) NOT NULL,
    section    VARCHAR(50) NOT NULL,
    content    TEXT        NOT NULL,
    embedding  vector(1536)
);
CREATE INDEX idx_doc_chunks_type ON doc_chunks (doc_type);

-- rag MCP용 읽기 전용 계정(init 스크립트가 만들어 둠)에 doc_chunks 조회만 허용한다. 업무 테이블은 보지 못한다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rag_reader') THEN
        GRANT SELECT ON doc_chunks TO rag_reader;
    END IF;
END
$$;

-- ============================================================
-- 4. 업무 흐름: RFQ, AI 작업, 분석 결과, 승인
-- ============================================================
CREATE TABLE rfqs (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES users (id),
    -- 요청서에 적힌 RFQ 번호. Agent가 원문에서 추출해 채우므로 접수 시점에는 비어 있을 수 있다
    rfq_number  VARCHAR(30) UNIQUE,
    title       VARCHAR(200) NOT NULL,
    -- 요청서 원문(마크다운). 수량·납기·사양은 Agent가 여기서 추출한다
    content     TEXT         NOT NULL,
    status      VARCHAR(30)  NOT NULL DEFAULT 'SUBMITTED'
        CHECK (status IN ('SUBMITTED', 'ANALYZING', 'WAITING_APPROVAL', 'COMPLETED', 'ON_HOLD', 'FAILED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_rfqs_customer ON rfqs (customer_id, created_at DESC);

-- Agent 실행 1회 = 작업 1건. thread_id는 LangGraph checkpointer가 승인 후 재개할 때 쓰는 키이다.
CREATE TABLE ai_tasks (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    rfq_id         BIGINT       NOT NULL REFERENCES rfqs (id),
    requested_by   BIGINT       NOT NULL REFERENCES users (id),
    status         VARCHAR(30)  NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'NEEDS_CLARIFICATION', 'WAITING_APPROVAL', 'REVALIDATING',
                          'REGISTERING', 'PARTIALLY_REGISTERED', 'ON_HOLD', 'DONE', 'FAILED')),
    current_step   VARCHAR(50),
    thread_id      VARCHAR(100) NOT NULL UNIQUE,
    -- 분석 요청 메시지의 멱등 키. 같은 요청이 두 번 들어와도 작업은 한 건만 만든다
    request_id     UUID         NOT NULL UNIQUE,
    failure_reason TEXT,
    trace_url      TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ
);
CREATE INDEX idx_ai_tasks_rfq ON ai_tasks (rfq_id, created_at DESC);
CREATE INDEX idx_ai_tasks_status ON ai_tasks (status);

-- 반려 후 재분석하면 version이 올라간다. 승인·등록은 승인받은 version과 일치할 때만 허용한다.
-- JSONB 컬럼은 데이터셋 정답 JSON 구조를 그대로 담는다.
--   requirements  : 1_parsed_rfq.json (구조화된 요구사항)
--   product_match : 선정 모델·옵션·적합 유형·단가 (4_output_state.json의 selected_model, options, fit_type, quoted_price_krw ...)
--   shortages     : 부족 부품 목록 (Subgraph 2)
--   alternatives  : 대안 비교 (기본안 / 대체 부품 / 분할 납품)
--   review        : 이슈, 가정, 고객 질의서, 계약 리스크 (2_validation_issues.json, 4_output_state.json)
CREATE TABLE analysis_results (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id                 UUID        NOT NULL REFERENCES ai_tasks (id),
    rfq_id                  BIGINT      NOT NULL REFERENCES rfqs (id),
    version                 INT         NOT NULL CHECK (version > 0),
    status                  VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'WAITING_APPROVAL', 'APPROVED', 'REJECTED', 'SUPERSEDED', 'REGISTERED')),
    requirements            JSONB       NOT NULL DEFAULT '{}'::jsonb,
    product_match           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    shortages               JSONB       NOT NULL DEFAULT '[]'::jsonb,
    alternatives            JSONB       NOT NULL DEFAULT '[]'::jsonb,
    review                  JSONB       NOT NULL DEFAULT '{}'::jsonb,
    selected_alternative_id VARCHAR(50),
    proposal                TEXT,
    total_price             NUMERIC(16, 2),
    delivery_date           DATE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (task_id, version)
);
CREATE INDEX idx_analysis_results_rfq ON analysis_results (rfq_id, version DESC);

CREATE TABLE approvals (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_result_id BIGINT       NOT NULL REFERENCES analysis_results (id),
    approver_id        BIGINT       NOT NULL REFERENCES users (id),
    decision           VARCHAR(20)  NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    comment            VARCHAR(1000),
    decided_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_approvals_result ON approvals (analysis_result_id);

-- ============================================================
-- 5. 등록 결과: 내부 견적, 구매요청, 단계별 멱등 기록
-- ============================================================
CREATE TABLE quotes (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rfq_id             BIGINT         NOT NULL REFERENCES rfqs (id),
    -- 분석 결과 한 건당 견적은 한 건. 재시도해도 두 번 만들어지지 않는다
    analysis_result_id BIGINT         NOT NULL UNIQUE REFERENCES analysis_results (id),
    total_price        NUMERIC(16, 2) NOT NULL CHECK (total_price >= 0),
    currency           CHAR(3)        NOT NULL DEFAULT 'KRW',
    delivery_date      DATE,
    status             VARCHAR(20)    NOT NULL DEFAULT 'REGISTERED' CHECK (status IN ('REGISTERED', 'CANCELLED')),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_quotes_rfq ON quotes (rfq_id);

CREATE TABLE quote_items (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quote_id    BIGINT         NOT NULL REFERENCES quotes (id),
    model_id    VARCHAR(20) REFERENCES models (model_id),
    part_id     VARCHAR(20) REFERENCES parts (part_id),
    description VARCHAR(500)   NOT NULL,
    quantity    NUMERIC(12, 3) NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0)
);
CREATE INDEX idx_quote_items_quote ON quote_items (quote_id);

CREATE TABLE purchase_requests (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quote_id           BIGINT         NOT NULL UNIQUE REFERENCES quotes (id),
    analysis_result_id BIGINT         NOT NULL REFERENCES analysis_results (id),
    total_price        NUMERIC(16, 2) NOT NULL CHECK (total_price >= 0),
    status             VARCHAR(20)    NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED', 'CANCELLED')),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- supplier는 parts.supplier와 같은 문자열이다 (데이터셋에 공급업체 테이블이 없다)
CREATE TABLE purchase_request_items (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_request_id   BIGINT         NOT NULL REFERENCES purchase_requests (id),
    part_id               VARCHAR(20)    NOT NULL REFERENCES parts (part_id),
    supplier              VARCHAR(50)    NOT NULL,
    quantity              INT            NOT NULL CHECK (quantity > 0),
    unit_price            NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
    expected_arrival_date DATE
);
CREATE INDEX idx_pr_items_pr ON purchase_request_items (purchase_request_id);

-- 등록 요청(AI가 만든 request_id)의 단계별 결과. 견적과 구매요청을 따로 기록해
-- 일부만 실패했을 때 성공한 단계는 건너뛰고 실패한 단계만 재시도할 수 있다.
CREATE TABLE registration_requests (
    request_id         UUID        NOT NULL,
    step               VARCHAR(30) NOT NULL CHECK (step IN ('QUOTE', 'PURCHASE_REQUEST')),
    status             VARCHAR(20) NOT NULL CHECK (status IN ('SUCCEEDED', 'FAILED')),
    analysis_result_id BIGINT      NOT NULL REFERENCES analysis_results (id),
    resource_id        BIGINT,
    error_message      TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (request_id, step)
);

-- ============================================================
-- 6. 메시징: RabbitMQ 소비자 멱등 처리 기록 (at-least-once 전달 대비)
-- ============================================================
CREATE TABLE processed_messages (
    message_id   UUID         NOT NULL,
    consumer     VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, consumer)
);
