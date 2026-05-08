ALTER TABLE quizzes
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

UPDATE quizzes
SET updated_at = created_at;
