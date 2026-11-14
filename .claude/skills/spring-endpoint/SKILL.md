---
name: spring-endpoint
description: How to add or change an API endpoint in the One Percent Spring Boot backend (controller, service, repository, DTO, tests). Use this whenever you touch a controller, add a route, change what a route returns, add a repository query, or fix an authorisation/ownership bug in one_percent_spring, even for a "small" change, because every endpoint here must be scoped to the logged-in user and tested through the real security filter.
---

# Adding or changing an endpoint

Layers: `controller` (HTTP only) → `service` (rules) → `repository` (Mongo). DTOs + MapStruct mappers sit
between the API and the `model` classes. Keep each layer doing only its job; it's what makes the
services unit-testable without Spring.

## The rule that matters most: scope everything to the logged-in user

The user comes from the JWT (`SecurityContextHolder` principal is a `model.User`). **Never** take a user ID
from the path, query or body: anyone can type someone else's ID.

- By-ID access goes through a repository method that includes the owner, e.g.
  `Optional<X> findByIdAndUserId(String id, String userId)`, never plain `findById`.
- Not found *and* not yours both throw `ResourceNotFoundException` → 404, so the API never reveals that
  another user's record exists. `JournalService.getOwnedJournalEntry` is the reference implementation.
- On create, ignore any client-supplied `id`/`userId`; set them server-side.

`GlobalExceptionHandler` maps `ResourceNotFoundException` → 404, `IllegalArgumentException` → 400 (its
message is shown to users, so write it for them), anything else → 500.

## Steps

1. DTO in `dto/` (what the client sends/receives; never expose embeddings, password hashes or `userId` input).
2. Repository query (owner-scoped).
3. Service method: get the authenticated user, apply the rule, return a DTO.
4. Controller method: thin, returns `ResponseEntity<Dto>`; no `Optional` in responses.
5. Tests (below). 6. If a route or shape changed, run the `api-contract-sync` skill.

## Tests: two kinds, both needed

**Service unit test** (Mockito, no Spring; see `JournalServiceTest`):
- `TestAuth.loginAs("alice")` in `@BeforeEach`, `TestAuth.clear()` in `@AfterEach`
- real mapper via `Mappers.getMapper(XMapper.class)`
- always include the "another user's record" case and assert nothing was saved/deleted
  (`verify(repo, never()).save(any())`)

**Web-layer test** through the real security chain (see `JournalControllerTest`):
```java
@WebMvcTest(XController.class)
@Import({SecurityConfig.class, WebConfig.class})
// @MockBean the service, JwtUtil and UserRepository; stub jwtUtil.extractEmail + userRepository.findByEmail
```
Cover: no token → rejected, own record → 200, someone else's → 404, bad input → 400.

Tests must run offline: mock `OpenAIClient` (or `spy` it and stub `sendChatCompletion`), never call OpenAI
or need MongoDB. Anything needing the real DB goes behind `RUN_INTEGRATION_TESTS=true`.

## Don't break the mobile app

The installed mobile app calls today's routes. Add new routes or optional fields rather than changing
existing ones; if an old route stays, keep a test on it.
