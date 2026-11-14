# CLAUDE.md

One Percent backend: Spring Boot 3.1 + MongoDB + OpenAI (Java 17, Maven).
The mobile frontend is a separate repo at `~/projects/one_percent_mobile` (remote `fyp_one_percent`).
Work in this copy (`~/projects/one_percent_spring`, Linux filesystem; open it in IntelliJ via `\\wsl$\Ubuntu\home\kai\projects`).
The old copy on `/mnt/c/Users/Kai Lau/Documents/work/one_percent_spring` is only a backup.

## Git workflow (always follow)

- **Never work directly on `main`.** Before making any change, create a feature branch from an up-to-date `main`:
  `git checkout main && git pull && git checkout -b feature/<short-kebab-description>`
  (use `fix/<...>` for bug fixes, `test/<...>` for test-only work).
- **One commit per feature.** As soon as each individual feature or fix is finished, and its tests pass, commit it on its own
  with a clear message describing what changed and why. Don't bundle unrelated changes into one commit.
- **Push after every commit:** `git push -u origin <branch>` (first push) / `git push` afterwards.
- Run the tests before each commit (see below). Don't commit with failing tests.
- Never commit secrets: `src/main/resources/application.properties` is gitignored and must stay that way.

## Commands

- Run tests: `mvn test`. They're offline unit/web-layer tests (no MongoDB or OpenAI needed).
- Integration tests against the real DB and OpenAI: `RUN_INTEGRATION_TESTS=true mvn test`.
- Run the app: `mvn spring-boot:run` (needs a local `application.properties`, see README).

## Conventions

- Anything fetched by ID must be scoped to the authenticated user (e.g. `findByIdAndUserId`) and throw
  `ResourceNotFoundException` (404) when it isn't theirs. Never trust a `userId` from the request body or path.
- `GlobalExceptionHandler` maps `ResourceNotFoundException` → 404, `IllegalArgumentException` → 400, everything else → 500.
