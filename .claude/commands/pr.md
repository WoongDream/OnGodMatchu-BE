---
description: dev 브랜치로 PR 생성 (code-review 없음)
---

Run the following steps in order:

1. Run `git log dev..HEAD --oneline` and `git diff dev...HEAD --stat` to review what will go into the PR. Only fetch full diff for specific files if needed for the title/body.

2. Push current branch to origin if not already pushed: `git push -u origin HEAD`

3. Create a PR using `gh pr create` with:
   - Title: `[type] 변경 내용` (type: feat | fix | refactor | style | docs | chore | remove)
   - Body: bullet-point summary of what changed and why. Do NOT include any Claude or AI attribution text.
   - Base branch: `dev`

4. Output the PR URL.
