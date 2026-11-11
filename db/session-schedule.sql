-- 세션 예정 시작/종료 일시(선택 입력)를 추가한다.
-- 값은 서버 시간대(KST) 기준으로 저장하고, API에서는 +09:00을 붙여 주고받는다.
BEGIN;
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS scheduled_start_at TIMESTAMP;
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS scheduled_end_at TIMESTAMP;
COMMIT;