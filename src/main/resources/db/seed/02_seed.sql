-- =====================================================================
-- 목업 데이터 v2.1 (모든 회사·제품·수치는 가상)
-- 정답: EWP-12-B + OPT-HT + OPT-IP9K, 단가 37,300원
-- 함정: P-MCU-G1 가용 1,500 (시작품 2,000에 500 부족), 입고 2027-01-20 → 대체 칩 P-MCU-G1-ALT
-- =====================================================================

-- 사용자·인증 ----------------------------------------------------------
INSERT INTO companies (company_id, name, company_type) VALUES
(1, '한별모터스', 'CUSTOMER'),
(2, '대성오토텍', 'SUPPLIER');

-- 데모 계정 비밀번호: demo1234 (BCrypt)
INSERT INTO users (user_id, login_id, password_hash, company_id, name, user_type) VALUES
(1, 'hb_buyer01', '$2a$10$TaOEEyLczgigwRYprZufWeyhF.VPYRfwBkogVn3gJfQ0ZWItHJH.i', 1, '박구매', 'CUSTOMER'),
(2, 'ds_sales01', '$2a$10$TaOEEyLczgigwRYprZufWeyhF.VPYRfwBkogVn3gJfQ0ZWItHJH.i', 2, '김영업', 'MANAGER');

-- 제품 ----------------------------------------------------------------
-- A: 작고 싸지만 유량 부족 / B: 옵션 2개로 충족 (정답) / C: 소음·무게 초과
INSERT INTO models VALUES
('EWP-12-A', '소형 워터펌프', 15.0,  50, 9, 16, -40, 105, 'IP67',   43, 'PWM',     100, 85, 80, 0.55, 29000),
('EWP-12-B', '중형 워터펌프', 25.0,  80, 9, 16, -40, 110, 'IP67',   42, 'LIN,PWM', 110, 90, 90, 0.70, 34000),
('EWP-12-C', '대형 워터펌프', 35.0, 100, 9, 16, -40, 125, 'IP6K9K', 48, 'LIN',     118, 98, 94, 0.95, 45000);

-- 부품 ----------------------------------------------------------------
INSERT INTO parts VALUES
('P-MTR-BL12',   '모터',                  '한결모터',   9500,  45, TRUE),
('P-MCU-STD',    '제어칩 (표준)',         '세온반도체', 3200,  60, TRUE),
('P-MCU-G1',     '제어칩 (고온용)',       '세온반도체', 5300, 120, TRUE),
('P-MCU-G1-ALT', '제어칩 (고온용, 대체)', '누리칩스',   5600,  30, TRUE),
('P-IMP-25',     '임펠러',                '대성 자체',  1200,  14, FALSE),
('P-HSG-AL',     '알루미늄 케이스',       '동명금속',   4100,  30, FALSE),
('P-SEAL-STD',   '방수 실링 (표준)',      '우성고무',    400,  21, FALSE),
('P-SEAL-9K',    '방수 실링 (강화)',      '우성고무',    950,  21, FALSE),
('P-CON-4P',     '4핀 커넥터',            '케이커넥트',  600,  21, FALSE);

-- 옵션 ----------------------------------------------------------------
INSERT INTO options VALUES
('OPT-HT',   '고온 대응 (125℃)',     'temp_max',  '125',    0.00, 0, 2400, 'P-MCU-STD',  'P-MCU-G1'),
('OPT-IP9K', '방수 강화 (IP6K9K)',  'ip_rating', 'IP6K9K', 0.05, 1,  900, 'P-SEAL-STD', 'P-SEAL-9K');

INSERT INTO model_options VALUES
('EWP-12-A', 'OPT-HT'),
('EWP-12-B', 'OPT-HT'),
('EWP-12-B', 'OPT-IP9K');

-- BOM (옵션 적용 전 기본 구성) ------------------------------------------
INSERT INTO bom VALUES
('EWP-12-A', 'P-MTR-BL12', 1), ('EWP-12-A', 'P-MCU-STD', 1), ('EWP-12-A', 'P-IMP-25', 1),
('EWP-12-A', 'P-HSG-AL',   1), ('EWP-12-A', 'P-SEAL-STD', 1), ('EWP-12-A', 'P-CON-4P', 1),
('EWP-12-B', 'P-MTR-BL12', 1), ('EWP-12-B', 'P-MCU-STD', 1), ('EWP-12-B', 'P-IMP-25', 1),
('EWP-12-B', 'P-HSG-AL',   1), ('EWP-12-B', 'P-SEAL-STD', 1), ('EWP-12-B', 'P-CON-4P', 1);

-- 재고 ----------------------------------------------------------------
INSERT INTO inventory VALUES
('P-MTR-BL12',   18000,  6000, 10000, '2026-11-20'),
('P-MCU-STD',    12000,  4000,     0, NULL),
('P-MCU-G1',      9000,  7500,  5000, '2027-01-20'),   -- 가용 1,500 → 500 부족, 입고 36일 늦음
('P-MCU-G1-ALT',  3000,     0,     0, NULL),           -- 대체 칩, 가용 3,000
('P-IMP-25',     22000,  4000,     0, NULL),
('P-HSG-AL',     15000,  5000,  8000, '2026-12-01'),
('P-SEAL-STD',   20000,  6000,     0, NULL),
('P-SEAL-9K',     8000,  1000,     0, NULL),
('P-CON-4P',     30000, 10000,     0, NULL);

-- 대체 부품 -------------------------------------------------------------
INSERT INTO part_substitutes VALUES
('P-MCU-G1', 'P-MCU-G1-ALT', 14, TRUE, 'alt-002');

-- RFQ (접수 직후 상태: 고객 요구 컬럼은 SG1이 채움) ----------------------
INSERT INTO rfqs (rfq_id, rfq_number, customer_company_id, uploaded_by, item, file_path, status, received_at) VALUES
(1, 'HB-EV7-EWP-001', 1, 1, '전동식 워터펌프 (EWP, 12V)', '01_rfq/RFQ_HB-EV7-EWP-001.md', 'RECEIVED', '2026-10-12 09:00');

-- rfq_reviews, quotes: Agent 실행 시 Spring API로 저장 (시드 없음)
-- doc_chunks: 03_doc_chunks.sql

-- 명시적으로 넣은 ID 뒤로 시퀀스 이동
SELECT setval('companies_company_id_seq', 2);
SELECT setval('users_user_id_seq', 2);
SELECT setval('rfqs_rfq_id_seq', 1);
