-- 데모 데이터. demo 프로파일에서만 적재된다 (DemoDataLoader). Flyway 마이그레이션이 아니다.
-- 원본: 개인폴더/Input/rfq_dataset (지영님). INSERT 값은 02_seed.sql 그대로이고, FK 때문에 parts를 options보다 먼저 넣도록 순서만 바꿨다.
-- 시나리오: 한별모터스 RFQ HB-EV7-EWP-001 → 정답 EWP-12-B + OPT-HT + OPT-IP9K (37,300원)
--           함정: P-MCU-G1(고온 제어칩) 가용 재고 1,500개 < 시작품 2,000개 + 입고 예정(2027-01-20)이 납기(2026-12-15)보다 늦음

-- models
INSERT INTO models VALUES
('EWP-12-A', '소형 워터펌프',   15.0, 50, 9, 16, -40, 105, 'IP67',   43, 'PWM',     100,  85, 80, 0.55, 29000),
('EWP-12-B', '중형 워터펌프',   25.0, 80, 9, 16, -40, 110, 'IP67',   42, 'LIN,PWM', 110,  90, 90, 0.70, 34000),
('EWP-12-C', '대형 워터펌프',   35.0, 100, 9, 16, -40, 125, 'IP6K9K', 48, 'LIN',     118,  98, 94, 0.95, 45000);

-- parts
INSERT INTO parts VALUES
('P-MTR-BL12',  '모터',                 '한결모터',     9500,  45, TRUE),
('P-MCU-STD',   '제어칩 (표준)',        '세온반도체',   3200,  60, TRUE),
('P-MCU-G1',    '제어칩 (고온용)',      '세온반도체',   5300, 120, TRUE),
('P-MCU-G1-ALT','제어칩 (고온용, 대체)', '누리칩스',     5600,  30, TRUE),
('P-IMP-25',    '임펠러 (회전 날개)',   '대성 자체',    1200,  14, FALSE),
('P-HSG-AL',    '알루미늄 케이스',      '동명금속',     4100,  30, FALSE),
('P-SEAL-STD',  '방수 실링 (표준)',     '우성고무',      400,  21, FALSE),
('P-SEAL-9K',   '방수 실링 (강화)',     '우성고무',      950,  21, FALSE),
('P-CON-4P',    '4핀 커넥터',           '케이커넥트',    600,  21, FALSE);

-- options
INSERT INTO options VALUES
('OPT-HT',   '고온 대응 (125℃)',     'temp_max',  '125',    0.00, 0, 2400, 'P-MCU-STD',  'P-MCU-G1'),
('OPT-IP9K', '고압 세차 방수 강화', 'ip_rating', 'IP6K9K', 0.05, 1,  900, 'P-SEAL-STD', 'P-SEAL-9K');

-- model_options
INSERT INTO model_options VALUES
('EWP-12-A', 'OPT-HT'),
('EWP-12-B', 'OPT-HT'),
('EWP-12-B', 'OPT-IP9K');

-- bom
INSERT INTO bom VALUES
('EWP-12-B', 'P-MTR-BL12', 1),
('EWP-12-B', 'P-MCU-STD',  1),
('EWP-12-B', 'P-IMP-25',   1),
('EWP-12-B', 'P-HSG-AL',   1),
('EWP-12-B', 'P-SEAL-STD', 1),
('EWP-12-B', 'P-CON-4P',   1),
('EWP-12-A', 'P-MTR-BL12', 1),
('EWP-12-A', 'P-MCU-STD',  1),
('EWP-12-A', 'P-IMP-25',   1),
('EWP-12-A', 'P-HSG-AL',   1),
('EWP-12-A', 'P-SEAL-STD', 1),
('EWP-12-A', 'P-CON-4P',   1);

-- inventory
INSERT INTO inventory VALUES
('P-MTR-BL12',   18000,  6000, 10000, '2026-11-20'),
('P-MCU-STD',    12000,  4000,     0, NULL),
('P-MCU-G1',      9000,  7500,  5000, '2027-01-20'),
('P-MCU-G1-ALT',  3000,     0,     0, NULL),
('P-IMP-25',     22000,  4000,     0, NULL),
('P-HSG-AL',     15000,  5000,  8000, '2026-12-01'),
('P-SEAL-STD',   20000,  6000,     0, NULL),
('P-SEAL-9K',     8000,  1000,     0, NULL),
('P-CON-4P',     30000, 10000,     0, NULL);

-- [신규] 대체 부품: P-MCU-G1 → P-MCU-G1-ALT (alt_parts_criteria.md 제2조: 핀 배치 동일, 처음 쓰는 공급사라 2주 검증 시험, 개당 +300원)
INSERT INTO part_substitutes (part_id, substitute_part_id, compatibility_note, validation_days, requires_spec_revalidation) VALUES
('P-MCU-G1', 'P-MCU-G1-ALT', '같은 고온 등급, 핀 배치 동일로 회로 변경 불필요. 처음 쓰는 공급사(누리칩스)라 적용 전 검증 시험 필요', 14, TRUE);

-- 데모 사용자 (비밀번호는 모두 demo1234. 데모 전용)
INSERT INTO users (email, password_hash, name, company, role) VALUES
('customer@hanbyeol.example', '$2a$10$B9G7cIDZXwOLQ3Vq.4PftunnA6WjV1l28hcgvBT/K5MRPdoLgFV82', '한별 구매담당', '한별모터스', 'CUSTOMER'),
('sales@daesung.example',     '$2a$10$B9G7cIDZXwOLQ3Vq.4PftunnA6WjV1l28hcgvBT/K5MRPdoLgFV82', '대성 영업담당', '대성오토텍', 'MANAGER');

-- 문서 청크 22개 (chunks.jsonl). embedding은 비워 두고 임베딩 적재 작업이 채운다.
INSERT INTO doc_chunks (chunk_id, doc_type, source, section, content) VALUES
('dsn-001', 'design_std', 'design_standard.md', '1. 적합성 판정 원칙',
 '고객 요구 사양은 항목별로 표준 모델 사양과 비교한다. 모든 항목이 기본 사양으로 충족되면 ''표준'', 옵션을 붙여 충족되면 ''파생 사양'', 옵션으로도 충족되지 않아 케이스나 모터를 바꿔야 하면 ''신규 개발''로 분류한다. 여러 모델이 후보일 때는 옵션 수가 적고 단가가 낮은 모델을 우선 추천한다.'),
('dsn-002', 'design_std', 'design_standard.md', '2. 유량 판단',
 '유량은 반드시 수두(압력) 조건과 함께 비교한다. 우리 모델의 유량이 고객 요구보다 크고, 우리 기준 수두가 고객 기준 수두보다 같거나 높으면 충족으로 본다. 차량용 냉각 펌프의 유량은 통상 분당 리터(L/min)로 표기하며, 시간당 리터(L/h)로 적힌 값이 1 L/min 미만으로 환산되면 단위 표기 오류일 가능성이 높으므로 고객 확인 대상이다.'),
('dsn-003', 'design_std', 'design_standard.md', '3. 작동 온도',
 '요구 최고 온도가 표준 모델 상한을 넘으면 고온 대응 옵션(OPT-HT)을 우선 검토한다. OPT-HT는 제어칩을 고온용으로 교체해 상한을 125℃까지 올린다. 요구 온도가 125℃를 넘으면 신규 개발로 분류한다. 배터리팩 인접 장착은 고온 노출이 잦아 OPT-HT 적용을 권장한다.'),
('dsn-004', 'design_std', 'design_standard.md', '4. 방수 등급',
 '방수 등급은 IP67보다 IP6K9K가 높다. IP67은 일시적 침수에 견디는 수준이고, IP6K9K는 고압·고온 세차 물줄기에도 견디는 수준이다. 고압 세차 노출 위치에 장착되는 경우 IP6K9K가 필요하며, 표준 모델이 IP67이면 방수 강화 옵션(OPT-IP9K)을 적용하고 방수 시험을 다시 수행한다.'),
('dsn-005', 'design_std', 'design_standard.md', '5. 소음',
 '소음은 1 m 거리, 정격 유량 조건에서 측정한 값으로 비교한다. 옵션 적용으로 소음이 늘어나는 경우 늘어난 값으로 판정한다. 소음 요구를 초과하는 모델은 다른 항목이 충족되더라도 추천 대상에서 제외한다.'),
('dsn-006', 'design_std', 'design_standard.md', '6. 크기와 무게',
 '외형 크기는 가로·세로·높이 각각이 고객 최대 치수 이내여야 한다. 무게는 옵션 추가 무게를 더한 값으로 판정한다. 크기나 무게를 줄이려면 케이스 재설계가 필요하므로 신규 개발로 분류한다.'),
('dsn-007', 'design_std', 'design_standard.md', '7. 커넥터와 냉각수',
 '커넥터 형상과 냉각수 종류는 설계 확정에 필요한 필수 정보다. 표준 커넥터는 4핀이며, 표준 냉각수는 에틸렌글리콜 50% 혼합액이다. 고객 사양서에 미기재되었거나 ''TBD''인 경우 표준값을 가정해 검토하되, 반드시 고객 확인 질의 목록에 포함한다.'),
('cat-001', 'catalog', 'product_catalog.md', '1. 제품 개요',
 '전동식 워터펌프는 전기차의 배터리와 모터를 식히는 냉각수를 순환시키는 부품이다. 엔진 동력이 아닌 전기 모터로 돌아가므로 필요한 만큼만 냉각수를 보낼 수 있다. EWP-12 시리즈는 모두 12V 차량 전원에서 동작한다.'),
('cat-002', 'catalog', 'product_catalog.md', '2. EWP-12-A (소형)',
 '소형 차량이나 보조 냉각 라인용 모델이다. 작고 가벼우며 가격이 가장 낮지만 유량이 분당 15리터로 시리즈 중 가장 적다. 통신은 PWM만 지원한다. 주행용 배터리팩 같은 대용량 냉각에는 적합하지 않다.'),
('cat-003', 'catalog', 'product_catalog.md', '3. EWP-12-B (중형)',
 '시리즈의 주력 모델이다. 분당 25리터의 유량과 42 dB의 낮은 소음이 특징이며, LIN과 PWM 통신을 모두 지원한다. 중형 전기차 배터리 냉각에 가장 많이 적용된다. 고온 대응 옵션과 방수 강화 옵션을 붙일 수 있어 다양한 장착 환경에 대응한다.'),
('cat-004', 'catalog', 'product_catalog.md', '4. EWP-12-C (대형)',
 '대형 차량과 고출력 모터 냉각용 모델이다. 유량이 가장 크고 기본 사양으로 125℃와 IP6K9K를 만족하지만, 모터가 커서 소음(48 dB)과 무게(0.95 kg)가 크고 가격이 가장 높다. 소음이나 무게 제한이 있는 차량에는 권장하지 않는다.'),
('cat-005', 'catalog', 'product_catalog.md', '5. 옵션 OPT-HT (고온 대응)',
 '제어칩을 고온용으로 바꿔 작동 온도 상한을 125℃까지 높인다. 무게와 소음 변화는 없다. 배터리팩 하단처럼 열이 많은 위치에 권장한다. 고온용 제어칩은 업계 공급이 빠듯해 조달 기간이 길 수 있다.'),
('cat-006', 'catalog', 'product_catalog.md', '6. 옵션 OPT-IP9K (방수 강화)',
 '실링을 강화해 방수 등급을 IP67에서 IP6K9K로 높인다. 무게가 0.05 kg, 소음이 1 dB 늘어난다. 차체 하부처럼 고압 세차 물줄기가 닿는 위치에 권장한다.'),
('pol-001', 'policy', 'sales_policy.md', '제10조 견적 검토 원칙',
 '모든 견적은 기술 적합성, 부품 조달 가능성, 계약 조건을 함께 검토한 뒤 제출한다. 고객 사양서에 모순되거나 누락된 정보가 있으면 견적 제출 전에 고객에게 서면으로 확인을 요청한다.'),
('pol-002', 'policy', 'sales_policy.md', '제11조 연간 가격 인하',
 '고객이 요구하는 연간 가격 인하율(CR)은 연 3% 이내에서 수용할 수 있다. 3%를 초과하는 경우 영업본부장의 승인이 필요하다.'),
('pol-003', 'policy', 'sales_policy.md', '제12조 품질 보증 기간',
 '품질 보증 기간은 양산 시작일로부터 3년 또는 주행거리 100,000 km 중 먼저 도래하는 기간을 넘을 수 없다. 이를 초과하는 보증 조건은 품질본부장의 승인이 필요하며, 견적 단가에 보증 비용을 반영해야 한다.'),
('pol-004', 'policy', 'sales_policy.md', '제13조 지연 배상',
 '납기 지연에 대한 배상 책임은 해당 계약 금액의 10%를 상한으로 한다. 배상 상한이 없거나 10%를 초과하는 조항은 수용할 수 없으며, 고객과 상한 설정을 협의해야 한다.'),
('pol-005', 'policy', 'sales_policy.md', '제14조 단종 통지',
 '주요 부품의 단종 통지 의무는 12개월 이내 기간에서 수용할 수 있다.'),
('pol-006', 'policy', 'sales_policy.md', '제15조 결제 조건',
 '결제 기간은 월말 마감 후 90일 이내까지 수용할 수 있다.'),
('alt-001', 'alt_parts', 'alt_parts_criteria.md', '1. 대체 부품 검토 조건',
 '핵심 부품의 가용 재고가 필요 수량보다 적고, 입고 예정일이 고객 납기보다 늦으면 대체 부품을 검토한다. 대체 부품은 원래 부품과 같은 성능 등급 이상이어야 한다.'),
('alt-002', 'alt_parts', 'alt_parts_criteria.md', '2. 제어칩 대체',
 '고온용 제어칩(P-MCU-G1)의 대체품은 누리칩스의 P-MCU-G1-ALT다. 같은 고온 등급이며 핀 배치가 같아 회로 변경이 필요 없다. 다만 처음 쓰는 공급사이므로 적용 전 2주간의 검증 시험이 필요하고, 개당 원가가 300원 높다.'),
('alt-003', 'alt_parts', 'alt_parts_criteria.md', '3. 대안 비교 기준',
 '대안은 납기 충족 여부, 추가 원가, 검증 기간, 품질 위험 순으로 비교한다. 납기를 맞출 수 없는 대안은 다른 조건이 좋아도 우선순위에서 밀린다.');
