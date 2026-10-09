-- Spring이 사용하는 PostgreSQL에 적용한다. 기존 입장 시각은 복원할 수 없어 NULL을 유지한다.
ALTER TABLE questions ADD COLUMN IF NOT EXISTS answered_at TIMESTAMP;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS presenter_requested_at TIMESTAMP;
ALTER TABLE session_participants ADD COLUMN IF NOT EXISTS joined_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS question_likes (
    question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (question_id, user_id)
);
