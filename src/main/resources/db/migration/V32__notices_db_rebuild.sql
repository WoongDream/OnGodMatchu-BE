-- 공지사항을 markdown 정적 파일에서 DB 기반으로 재구축 (V22 생성 → V23 drop 후 신규 스키마).
-- 상태(임시저장/게시) · 고정 · 조회수 · 작성/수정 시각 관리. 기존 markdown 공지는 이관하지 않는다(새 시작).
CREATE TABLE notices (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    content      TEXT NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    pinned       BOOLEAN NOT NULL DEFAULT FALSE,
    view_count   BIGINT NOT NULL DEFAULT 0,
    published_at TIMESTAMP NULL,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NULL
);

-- 공개 목록: 게시 + 고정 우선 + 최신순
CREATE INDEX idx_notices_public ON notices (status, pinned DESC, published_at DESC);
