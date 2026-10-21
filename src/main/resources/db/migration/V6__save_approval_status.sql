-- SG3 저장 직전 승인 대기 상태를 추가한다. 이미 적용한 V2 파일은 수정하지 않는다.
ALTER TABLE ai_tasks DROP CONSTRAINT ai_tasks_status_check;
ALTER TABLE ai_tasks ADD CONSTRAINT ai_tasks_status_check
    CHECK (status IN ('QUEUED', 'RUNNING', 'NEEDS_CLARIFICATION', 'WAITING_SAVE_APPROVAL', 'WAITING_APPROVAL',
                      'REVALIDATING', 'REGISTERING', 'ON_HOLD', 'DONE', 'FAILED'));
