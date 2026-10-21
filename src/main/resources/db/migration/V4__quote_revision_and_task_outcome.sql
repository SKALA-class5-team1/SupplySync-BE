ALTER TABLE quotes ADD COLUMN revision SMALLINT;
ALTER TABLE quotes ADD COLUMN revision_requested BOOLEAN NOT NULL DEFAULT FALSE;

-- 기존 견적의 순서를 보존한다. 이후 Agent 저장 요청은 revision을 명시한다.
WITH numbered AS (
    SELECT quote_id, row_number() OVER (PARTITION BY review_id ORDER BY created_at, quote_id) AS number
    FROM quotes
)
UPDATE quotes q SET revision = numbered.number
FROM numbered WHERE q.quote_id = numbered.quote_id AND numbered.number <= 4;

CREATE UNIQUE INDEX uq_quotes_review_revision ON quotes(review_id, revision)
    WHERE revision IS NOT NULL;
ALTER TABLE quotes ADD CONSTRAINT ck_quotes_revision CHECK (revision BETWEEN 1 AND 4);

ALTER TABLE ai_tasks ADD COLUMN outcome VARCHAR(20)
    CONSTRAINT ck_ai_tasks_outcome CHECK (outcome IN ('REGISTERED', 'REJECTED'));
UPDATE ai_tasks SET outcome = 'REGISTERED'
WHERE status = 'DONE' AND EXISTS (
    SELECT 1 FROM rfq_reviews r JOIN quotes q ON q.review_id = r.review_id
    WHERE r.thread_id = ai_tasks.thread_id AND q.status = 'REGISTERED'
);
