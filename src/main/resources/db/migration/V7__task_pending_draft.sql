-- SG3가 저장 승인을 기다리는 동안 담당자에게 보여 줄 견적 초안. 정식 견적(quotes)과 분리해 임시로 보관한다.
ALTER TABLE ai_tasks ADD COLUMN pending_draft JSONB;
