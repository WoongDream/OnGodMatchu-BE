UPDATE quizzes
SET category = CASE
    WHEN category = '연예인' THEN 'entertainment'
    WHEN category = '영화' THEN 'movie'
    WHEN category = '드라마' THEN 'drama'
    WHEN category IN ('애니', '애니메이션') THEN 'anime'
    WHEN category = '게임' THEN 'game'
    WHEN category = '음악' THEN 'music'
    WHEN category = '스포츠' THEN 'sports'
    WHEN category IN ('상식', '역사') THEN 'general'
    WHEN category IN ('entertainment', 'movie', 'drama', 'anime', 'game', 'music', 'sports', 'general', 'etc') THEN category
    ELSE 'etc'
END;
