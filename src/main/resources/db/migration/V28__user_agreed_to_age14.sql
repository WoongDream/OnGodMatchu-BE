-- 회원가입 만 14세 이상 동의 여부. 기존 가입 유저는 전부 테스트 계정이므로 TRUE 로 백필.
ALTER TABLE users ADD COLUMN agreed_to_age14 BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users SET agreed_to_age14 = TRUE;
