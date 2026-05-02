-- questions.image_url → image_key (rename + value: URL → key)
ALTER TABLE questions
    RENAME COLUMN image_url TO image_key;

UPDATE questions
SET image_key = substring(image_key FROM 'amazonaws\.com/(.+)$')
WHERE image_key LIKE 'http%';

ALTER TABLE questions
    ADD COLUMN answer_image_key VARCHAR(500);

-- quizzes.thumbnail_url → thumbnail_key
ALTER TABLE quizzes
    RENAME COLUMN thumbnail_url TO thumbnail_key;

UPDATE quizzes
SET thumbnail_key = substring(thumbnail_key FROM 'amazonaws\.com/(.+)$')
WHERE thumbnail_key LIKE 'http%';
