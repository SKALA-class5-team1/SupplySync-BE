-- 데이터셋 v2.2 원본 스키마는 유지하고 Agent 결과 저장 필드를 확장한다.
ALTER TABLE rfq_reviews ADD COLUMN final_plan JSONB;

ALTER TABLE quotes ADD COLUMN issue_date DATE;
ALTER TABLE quotes ADD COLUMN details JSONB;

-- 기존 견적은 번호에 발행일이 있으면 이를 복원하고, 그 외 ID만 생성 시각으로 보정한다.
UPDATE quotes SET issue_date = CASE
    WHEN quote_id ~ '^Q-[0-9]{4}-[0-9]{4}-[0-9]+$'
        THEN to_date(substring(quote_id from 3 for 9), 'YYYY-MMDD')
    ELSE created_at::date
END WHERE issue_date IS NULL;
