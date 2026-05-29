CREATE TABLE notices
(
    id           BIGSERIAL PRIMARY KEY,
    type         VARCHAR(20)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    content      TEXT         NOT NULL,
    published_at TIMESTAMP    NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notices_type_published ON notices (type, published_at DESC, id DESC);

INSERT INTO notices (type, title, content, published_at)
VALUES ('ANNOUNCEMENT',
        '온갓맞추 서비스 오픈 안내',
        '## 안녕하세요, 온갓맞추 입니다 👋

여러분의 지식·취향·기억을 한 장의 퀴즈로 만들어 친구들과 공유해보세요.

- **퀴즈 만들기** — 이미지·텍스트 단답형 퀴즈를 자유롭게 만들 수 있어요.
- **퀴즈 풀기** — 친구가 만든 퀴즈를 풀고 결과를 공유해보세요.
- **프로필** — 내가 만든 퀴즈와 푼 퀴즈 통계를 한눈에 볼 수 있어요.

문의는 언제든 환영합니다.',
        NOW()),
       ('RELEASE_NOTE',
        'v1.0.0 — 첫 릴리즈',
        '## v1.0.0 (첫 릴리즈)

- 퀴즈 만들기 / 풀기 기본 흐름
- 프로필 / 내가 만든 퀴즈 / 내가 푼 퀴즈
- 좋아요 · 댓글 · 공유 기능
- 공지사항 / 릴리즈 노트 페이지',
        NOW());
