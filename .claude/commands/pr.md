---
description: PR 생성 후 code-review 스킬로 자동 코드 리뷰 실행
---

Run the following steps in order:

1. Run `git log main..HEAD --oneline` and `git diff main...HEAD` to review all changes that will go into the PR.

2. Create a PR using `gh pr create` with:
   - Title: concise description following `[type] 변경 내용` format (type: feat | fix | refactor | style | docs | chore | remove)
   - Body: bullet-point summary of what changed and why. Do NOT include any Claude or AI attribution text.
   - Base branch: main

3. After the PR is created, note the PR number from the output.

4. Run the `code-review` skill on the newly created PR number (use the Skill tool with skill: "code-review:code-review" and args: "PR #<number>")
