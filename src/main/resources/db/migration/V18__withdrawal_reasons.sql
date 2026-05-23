-- 탈퇴 이유 익명 통계용. user_id 컬럼은 두지 않아 익명화 후에도 의미가 보존됨.
CREATE TABLE withdrawal_reasons
(
    id          BIGSERIAL PRIMARY KEY,
    reason_code VARCHAR(40) NOT NULL,
    reason_text VARCHAR(500),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_withdrawal_reasons_created_at ON withdrawal_reasons (created_at);
