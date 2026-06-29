-- 차단 닉네임 관리 — 예약어/금지어 규칙. raw_value=표시용 원본, normalized_value=Tier 0·1 정규화 매칭 키.
-- 매칭: EXACT(완전) / PREFIX(접두) / CONTAINS(부분). 유형은 분류 지표(금지 정책은 매칭 방식이 결정).
CREATE TABLE forbidden_nicknames (
    id               BIGSERIAL    PRIMARY KEY,
    raw_value        VARCHAR(100) NOT NULL,
    normalized_value VARCHAR(100) NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    match_type       VARCHAR(20)  NOT NULL,
    reason           VARCHAR(200) NULL,
    created_at       TIMESTAMP    NOT NULL
);

-- 완전·접두 매칭 가속 (정규화값은 문자·숫자만이라 prefix LIKE 가 인덱스 활용 가능)
CREATE INDEX idx_forbidden_nicknames_normalized ON forbidden_nicknames (normalized_value);
-- 같은 정규화값 + 같은 매칭 방식 중복 등록 차단
CREATE UNIQUE INDEX uq_forbidden_nicknames_norm_match
    ON forbidden_nicknames (normalized_value, match_type);

-- 기존 하드코딩 RESERVED(NicknamePolicy) 이관. 현 동작 보존 위해 완전 일치(EXACT)로 시드. 멱등(중복 무시).
INSERT INTO forbidden_nicknames (raw_value, normalized_value, type, match_type, reason, created_at)
VALUES
    ('관리자', '관리자', 'RESERVED', 'EXACT', '운영진 사칭 방지', NOW()),
    ('탈퇴한사용자', '탈퇴한사용자', 'RESERVED', 'EXACT', '시스템 예약어', NOW())
ON CONFLICT (normalized_value, match_type) DO NOTHING;
