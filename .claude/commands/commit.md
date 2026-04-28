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

Do NOT push. Do NOT run tests. Stop after the commit is complete.
