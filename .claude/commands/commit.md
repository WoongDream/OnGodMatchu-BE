---
description: Spotless 포맷 적용 후 [type] message 형식으로 커밋 생성 (push 없음)
---

Run the following steps in order:

1. Run `./gradlew spotlessApply` to auto-fix all Java formatting issues.

2. Run `git add .` to stage all changes including any files fixed by spotless.

3. Run `git diff --staged --stat` to review the file list. If output is small (under ~10 files), also run `git diff --staged` for content. For larger diffs, only inspect specific files needed to write the message.

4. Write a commit message following this format: `[type] message`
   - type: feat | fix | refactor | style | docs | chore | remove
   - message: concise description of the change in Korean or English

5. Commit with `git commit -m "[type] message"`

6. `TODO.md` 동기화 — 이번 커밋이 `## 진행 중` 항목을 끝낸 경우에만:
   - `TODO.md` 를 읽고 `## 진행 중` 항목 중 이번 커밋 diff 로 실제 끝난 것만 골라낸다 (애매하면 그대로 둔다).
   - 골라낸 항목을 `/todo` 커맨드의 1단계 규칙대로 `## 완료` 로 옮긴다 (`### 제목 — <hash>` 그룹 + `[x]` 결과물 2~6개로 압축, `## 완료` 맨 위에 추가).
   - `<hash>` 는 방금 만든 커밋의 short hash (`git rev-parse --short HEAD`).
   - `TODO.md` 는 `.gitignore` 로 추적 제외라 git 에 반영되지 않음. 별도 커밋 없음.
   - 진행 중에 옮길 항목이 없으면 이 단계는 건너뛴다.

Do NOT push. Do NOT run tests. Stop after the commit (and optional TODO sync) is complete.
