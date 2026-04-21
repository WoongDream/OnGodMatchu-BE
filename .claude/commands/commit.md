---
description: Spotless 포맷 적용 후 [type] message 형식으로 커밋 생성 (push 없음)
---

Run the following steps in order:

1. Run `./gradlew spotlessApply` to auto-fix all Java formatting issues.

2. Run `git add .` to stage all changes including any files fixed by spotless.

3. Run `git diff --staged` to review what will be committed.

4. Run related tests for staged files:
   - Identify staged `.java` files under `src/main/java/` (excluding test files)
   - For each staged file, derive the fully qualified class name from its path
     (e.g., `src/main/java/com/example/FooService.java` → `com.example.FooService`)
   - Find corresponding test classes in `src/test/java/` by appending `Test`
     (e.g., `com.example.FooServiceTest`)
   - Run `./gradlew test --tests "com.example.FooServiceTest" ...` for those test classes only
   - If no corresponding test files exist, skip this step
   - If any tests fail, stop and report the failures. Do NOT commit.

5. Write a commit message following this format: `[type] message`
   - type: feat | fix | refactor | style | docs | chore | remove
   - message: concise description of the change in Korean or English

6. Commit with `git commit -m "[type] message"`

Do NOT push. Stop after the commit is complete.
