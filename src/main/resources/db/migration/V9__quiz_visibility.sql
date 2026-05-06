ALTER TABLE quizzes
    ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE';

UPDATE quizzes
SET visibility = 'PUBLIC'
WHERE visibility = 'PRIVATE';
