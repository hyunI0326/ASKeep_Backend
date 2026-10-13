-- Spring DB의 Hibernate 기본 enum CHECK에 NO_EVIDENCE를 추가한다.
BEGIN;
ALTER TABLE questions DROP CONSTRAINT IF EXISTS questions_ai_status_check;
ALTER TABLE questions ADD CONSTRAINT questions_ai_status_check
    CHECK (ai_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'NO_EVIDENCE', 'FAILED'));
COMMIT;
