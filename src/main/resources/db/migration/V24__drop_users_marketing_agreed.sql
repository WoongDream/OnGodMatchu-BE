-- 마케팅 동의 정책 폐기 — 회원가입 시 마케팅 수신 동의를 받지 않음.
-- V21 에서 추가된 users.marketing_agreed 컬럼 제거.
ALTER TABLE users
    DROP COLUMN marketing_agreed;
