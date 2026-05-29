-- 카테고리 화이트리스트 10종 확정: 게임/음악/문화/방송/상식/만화/음식/인물/스포츠/병맛.
-- 살아남는 키: game, music, general, sports.
-- 사라지는 기존 키를 신규 키로 이전 (추천 매핑):
--   entertainment(연예인) → person(인물)
--   movie(영화)          → culture(문화)
--   drama(드라마)        → broadcast(방송)
--   anime(애니메이션)    → comic(만화)
--   etc(기타)            → general(상식)
-- 예상 밖의 레거시 값은 안전하게 general 로 흡수.
UPDATE quizzes
SET category = CASE
    WHEN category = 'entertainment' THEN 'person'
    WHEN category = 'movie' THEN 'culture'
    WHEN category = 'drama' THEN 'broadcast'
    WHEN category = 'anime' THEN 'comic'
    WHEN category = 'etc' THEN 'general'
    WHEN category IN (
        'game', 'music', 'culture', 'broadcast', 'general',
        'comic', 'food', 'person', 'sports', 'meme'
    ) THEN category
    ELSE 'general'
END;
