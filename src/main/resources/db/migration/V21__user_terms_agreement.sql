-- 가입 시점 약관 동의 기록. 1차에는 users 컬럼으로 관리 (재동의 history 는 후속 작업).
-- terms_version / privacy_version: 동의한 약관 버전 (TermsPolicy 상수 참조)
-- marketing_agreed: 마케팅 수신 동의 (옵셔널, 기본 false)
-- terms_agreed_at: 동의 시각
-- 기존 가입자는 NULL 유지 — 다음 로그인 시 재동의 유도는 후속 작업.
ALTER TABLE users
    ADD COLUMN terms_version    VARCHAR(10),
    ADD COLUMN privacy_version  VARCHAR(10),
    ADD COLUMN marketing_agreed BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN terms_agreed_at  TIMESTAMP;
