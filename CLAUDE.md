# OnGodMatchu Backend

## Absolute Rules
- Constructor injection only — no `@Autowired` (use `@RequiredArgsConstructor`)
- Never return Entity directly from Controller — always convert via `XxxResponse.from(entity)`
- All API responses wrapped in `ApiResponse<T>` (`global/response/`)
- Read-only service methods must have `@Transactional(readOnly = true)`
- Spotless (Google Java Format) must pass before every commit

## Commands
`./gradlew bootRun` · `test` · `spotlessApply` · `spotlessCheck`

## Domain
Korean quiz app — create & share image/text short-answer quizzes

## Package Structure
```
com.ongodmatchu/
├── domain/   ← 도메인별 controller / service / repository / entity / dto
├── global/   ← config, exception, response, util
└── infra/    ← s3, mail, ai
```

## References
Conventions: `docs/conventions.md`
