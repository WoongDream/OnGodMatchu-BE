-- 풀이 기록 카드 표기용: 풀이 당시 타이머 설정(timeLimitSec)과 상위 백분위 스냅샷을 attempt 에 영속화.
-- 둘 다 nullable — time_limit_sec NULL = 타이머 없음/레거시, top_percentile NULL = 첫 응시(응시 < 2)/레거시.
ALTER TABLE quiz_attempts ADD COLUMN time_limit_sec INT NULL;
ALTER TABLE quiz_attempts ADD COLUMN top_percentile DOUBLE PRECISION NULL;
