ALTER TABLE materials ADD COLUMN IF NOT EXISTS page_count INTEGER;

CREATE TABLE IF NOT EXISTS session_summary_sections (
    summary_id BIGINT NOT NULL REFERENCES session_summaries(id) ON DELETE CASCADE,
    section_index INTEGER NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    PRIMARY KEY (summary_id, section_index)
);
