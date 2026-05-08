-- 비로그인 풀이도 attempts 에 저장 → 퀴즈 작성자 기준 집계(weeklyPlayCount / correctRate)에 반영되도록 user_id NULL 허용
ALTER TABLE quiz_attempts
    ALTER COLUMN user_id DROP NOT NULL;
