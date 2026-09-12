# AI Shopping Agent — Backend Project Context (Handoff Doc)

This document describes the current state of the backend so another AI
(or developer) can continue building **Module 4 onward** without
re-reading the whole codebase. Modules 1–3 (foundation, auth,
conversation/message) are complete, tested, and should be treated as
stable — extend them, don't rewrite them.

---

## 1. Project concept

A user logs into a personal AI shopping agent and chats with it in
natural language (English/Hinglish), e.g.:

> "Mujhe 128GB ka pendrive chahiye under ₹1000."

A future AI module will parse this, search product sources, compare
results, and return recommendations. **None of that exists yet.** Only
the foundation exists:

```
User → Authentication → Conversation → Messages
```

---

## 2. Technology stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.4 |
| Build | Maven |
| Web | Spring Web (REST, no GraphQL/WebFlux) |
| Persistence | Spring Data JPA + PostgreSQL |
| Security | Spring Security (stateless) + JWT (jjwt 0.12.6) |
| Password hashing | BCrypt |
| Validation | Jakarta Bean Validation |
| Boilerplate | Lombok (`@Getter/@Setter/@Builder` on entities only) |
| Testing | JUnit 5, Mockito, Spring Boot Test, MockMvc, H2 (test-only) |

**Explicitly excluded** (do not introduce these in later modules
unless the user changes this constraint): Redis, Kafka, RabbitMQ,
Elasticsearch, microservices, Kubernetes, any paid infrastructure. This
is a ₹0-cost college MVP.

No AI APIs, no product/shopping APIs, no Spring AI yet — those are
Module 4+.

---

## 3. Package structure (modular monolith)

```
com.shoppingagent
├── ShoppingAgentApplication.java        # @SpringBootApplication entrypoint
│
├── config/
│   └── SecurityConfig.java              # filter chain, CORS, PasswordEncoder bean
│
├── security/
│   ├── JwtService.java                  # issue/validate/parse JWTs
│   ├── JwtAuthenticationFilter.java     # reads Authorization header, populates SecurityContext
│   ├── CustomUserDetailsService.java    # loads User by email for Spring Security
│   └── UserPrincipal.java               # UserDetails wrapper around the User entity
│
├── auth/
│   ├── AuthController.java              # POST /register, /login, GET /me
│   ├── AuthService.java                 # register/login business logic
│   └── dto/
│       ├── RegisterRequest.java
│       ├── LoginRequest.java
│       ├── RegisterResponse.java
│       └── AuthResponse.java
│
├── user/
│   ├── User.java                        # JPA entity
│   ├── UserRepository.java
│   ├── UserService.java                 # lookups + entity→DTO mapping
│   └── dto/UserResponse.java
│
├── chat/
│   ├── conversation/
│   │   ├── Conversation.java            # JPA entity, ManyToOne User, OneToMany Message
│   │   ├── ConversationRepository.java
│   │   ├── ConversationService.java     # CRUD + THE ownership-check method
│   │   ├── ConversationController.java
│   │   └── dto/
│   │       ├── ConversationCreateRequest.java
│   │       └── ConversationResponse.java
│   │
│   └── message/
│       ├── Message.java                 # JPA entity, ManyToOne Conversation
│       ├── MessageRole.java             # enum USER, ASSISTANT, SYSTEM
│       ├── MessageRepository.java
│       ├── MessageService.java          # currently just persists/retrieves — NO AI logic
│       ├── MessageController.java
│       └── dto/
│           ├── MessageCreateRequest.java
│           └── MessageResponse.java
│
├── common/
│   └── HealthController.java            # GET /api/health
│
└── exception/
    ├── GlobalExceptionHandler.java      # @RestControllerAdvice, consistent JSON error shape
    ├── ErrorResponse.java               # record: timestamp, status, error, message, path
    ├── DuplicateEmailException.java     # → 400
    ├── InvalidCredentialsException.java # → 401
    ├── ResourceNotFoundException.java   # → 404
    └── ForbiddenException.java          # → 403
```

Layering everywhere: **Controller → Service → Repository**. Constructor
injection only, no field injection. DTOs are always used at the
controller boundary — JPA entities are never returned directly.

---

## 4. Database schema

```
User (1) ──< Conversation (1) ──< Message
```

**users**
| column | type | notes |
|---|---|---|
| id | UUID (PK) | `GenerationType.UUID` |
| name | varchar, not null | |
| email | varchar, not null, unique | always stored lowercase/trimmed |
| password_hash | varchar, not null | BCrypt hash, never plain text |
| created_at | timestamp, not null | set in `@PrePersist` |
| updated_at | timestamp, not null | set in `@PrePersist`/`@PreUpdate` |

**conversations**
| column | type | notes |
|---|---|---|
| id | UUID (PK) | |
| user_id | UUID (FK → users.id), not null | `@ManyToOne(fetch = LAZY)` |
| title | varchar, not null | defaults to `"New Shopping Conversation"` if omitted at creation |
| created_at | timestamp, not null | |
| updated_at | timestamp, not null | |

**messages**
| column | type | notes |
|---|---|---|
| id | UUID (PK) | |
| conversation_id | UUID (FK → conversations.id), not null | `@ManyToOne(fetch = LAZY)` |
| role | varchar(20), not null | enum `MessageRole`: `USER`, `ASSISTANT`, `SYSTEM`, stored as STRING |
| content | TEXT, not null | |
| created_at | timestamp, not null | |

Deleting a `Conversation` cascades to delete its `Message`s
(`cascade = CascadeType.ALL, orphanRemoval = true` on the
`Conversation.messages` collection).

`spring.jpa.hibernate.ddl-auto=update` — no migration tool (Flyway/
Liquibase) is set up yet. If a future module needs schema changes,
either keep using `update` for this MVP stage or introduce a migration
tool deliberately (not currently present).

---

## 5. Authentication & security flow

1. `POST /api/auth/register` → validates input → normalizes email to
   lowercase → checks uniqueness → hashes password with BCrypt → saves
   `User` → returns `{ message, user }` (never the password/hash).
2. `POST /api/auth/login` → looks up user by normalized email →
   `passwordEncoder.matches()` → on success, `JwtService.generateToken(userId, email)`
   → returns `{ accessToken, tokenType: "Bearer", expiresIn, user }`.
3. Frontend sends `Authorization: Bearer <token>` on every subsequent
   request.
4. `JwtAuthenticationFilter` (a `OncePerRequestFilter`) reads the
   header, validates the token via `JwtService.isTokenValid()`, extracts
   the email (JWT subject), loads a `UserPrincipal` via
   `CustomUserDetailsService`, and sets it on the `SecurityContext`.
   Public endpoints (`/api/health`, `POST /api/auth/register`,
   `POST /api/auth/login`) skip the filter entirely
   (`shouldNotFilter`).
5. Controllers pull the current user via
   `@AuthenticationPrincipal UserPrincipal principal` →
   `principal.getUser()` (a real `User` entity) or `principal.getId()`
   (UUID). **The frontend can never pass a `userId` to control whose
   data is read/written** — this is enforced everywhere, not just on
   `/me`.
6. JWT secret and expiration come from environment variables
   (`JWT_SECRET`, `JWT_EXPIRATION`) — never hardcoded. See `.env.example`.

**`UserPrincipal`** (in `security/`) implements `UserDetails` and wraps
the `User` entity — this is the object any future module should use to
get "who is making this request."

---

## 6. Authorization rule for conversations/messages (important for later modules)

`ConversationService.getOwnedConversation(User user, UUID conversationId)`
is the **single source of truth** for ownership checking:
- Conversation doesn't exist → throws `ResourceNotFoundException` → 404.
- Conversation exists but belongs to another user → throws
  `ForbiddenException` → 403.
- Otherwise returns the `Conversation` entity.

`MessageService` calls this same method before touching any messages —
it does not re-implement the check. **Any future module that reads or
writes conversation/message data (the AI agent, product search, etc.)
should call through `ConversationService`/`MessageService` rather than
querying the repositories directly**, so this rule stays centralized.

---

## 7. API reference

### Public
| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/health` | — | `{ "status": "UP", "service": "shopping-agent-backend" }` |
| POST | `/api/auth/register` | `{ name, email, password }` | 201 `{ message, user: {id,name,email} }` |
| POST | `/api/auth/login` | `{ email, password }` | 200 `{ accessToken, tokenType, expiresIn, user }` |

### Authenticated (`Authorization: Bearer <token>`)
| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/auth/me` | — | `{ id, name, email }` (from JWT, not request params) |
| POST | `/api/conversations` | `{ title? }` | 201 `{ id, title, createdAt, updatedAt }` — title defaults to `"New Shopping Conversation"` |
| GET | `/api/conversations` | — | 200 array, newest first, only the caller's own |
| GET | `/api/conversations/{id}` | — | 200 conversation, or 403/404 |
| DELETE | `/api/conversations/{id}` | — | 204, cascades to delete messages |
| POST | `/api/conversations/{id}/messages` | `{ content }` | 201 `{ id, conversationId, role: "USER", content, createdAt }` — **stores only, no AI reply generated** |
| GET | `/api/conversations/{id}/messages` | — | 200 array, chronological (oldest first) |

### Error shape (consistent everywhere, via `GlobalExceptionHandler`)
```json
{
  "timestamp": "2026-09-12T12:00:00Z",
  "status": 403,
  "error": "FORBIDDEN",
  "message": "You do not have access to this conversation",
  "path": "/api/conversations/{id}"
}
```
No stack traces are ever leaked to clients.

---

## 8. Environment variables (`.env.example`)

```
DATABASE_URL=jdbc:postgresql://localhost:5432/shopping_agent
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your_password
JWT_SECRET=change_this_secret_to_a_long_random_string_at_least_32_chars
JWT_EXPIRATION=86400000
FRONTEND_URL=http://localhost:5173
```

`FRONTEND_URL` drives CORS config (`app.cors.allowed-origins` in
`application.yml`, consumed by `SecurityConfig.corsConfigurationSource()`).
Comma-separate multiple origins if needed later.

Tests run against a separate `application-test.yml` profile using an
in-memory H2 database — they need none of the above and never touch
real Postgres.

---

## 9. Dependency list (pom.xml highlights)

- `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
  `spring-boot-starter-security`, `spring-boot-starter-validation`
- `postgresql` (runtime)
- `io.jsonwebtoken:jjwt-api / jjwt-impl / jjwt-jackson` (0.12.6)
- `lombok` (optional, compile-time only)
- `spring-boot-devtools` (optional, runtime)
- Test: `spring-boot-starter-test`, `spring-security-test`,
  `com.h2database:h2`

Parent: `spring-boot-starter-parent:3.3.4`. `java.version=21`.

---

## 10. Tests already written

- `AuthIntegrationTest` — register success/duplicate/invalid payload,
  login success/wrong password, `/me` with/without token.
- `UserServiceTest` — BCrypt hash ≠ plaintext, `toResponse()` never
  leaks the password hash.
- `ConversationAndMessageIntegrationTest` — create/list own
  conversations, default title, 403 on another user's conversation,
  delete cascades to messages (404 after), message create + chronological
  retrieval, empty content rejected (400).
- `HealthControllerTest` — public health check.

All run via MockMvc against the real Spring Security filter chain,
using the `test` profile (H2). **Not yet executed in a real Maven
environment** (the environment this was built in had no Maven
installed and no network access to Maven Central) — run `mvn test`
locally before building on top of this to confirm a green baseline.

---

## 11. Explicit design decisions for future-module compatibility

- **No AI logic anywhere.** `MessageService.createUserMessage()` only
  persists what it's given. Module 4's future agent should call it to
  save the user's message, then separately save the assistant's reply
  once it has one — `MessageService` doesn't need to know how that
  reply was produced.
- **No product logic in the chat module**, and vice versa — keep new
  product-search/comparison/ranking modules as their own
  package(s) (e.g. `com.shoppingagent.product`, `com.shoppingagent.search`)
  rather than folding them into `chat`.
- **Keep `ChatService`-equivalents separate from a future
  `ShoppingAgentService`** — the plan (per original spec) is:
  - Module 4 → Spring AI
  - Module 5 → Product Search
  - Module 6 → Product Normalization
  - Module 7 → Comparison & Ranking
  - Module 8 → Complete Agent Pipeline
  - Module 9 → SSE streaming
- **Loose coupling**: new modules should depend on
  `ConversationService`/`MessageService`/`UserService` (or their DTOs),
  not on entities or repositories directly, so internals can change
  without breaking them.
- **UUIDs everywhere**, **DTOs at every controller boundary**,
  **constructor injection only** — keep following these conventions.
- **Do not hardcode secrets** — anything new (AI API keys, product API
  keys) should follow the same `${ENV_VAR}` pattern used for
  `JWT_SECRET`/`DATABASE_*`, added to `application.yml` and
  `.env.example`.

---

## 12. What is intentionally NOT built yet

- No AI/LLM integration (Spring AI or otherwise).
- No product search, scraping, or shopping API integration.
- No product/recommendation entities or endpoints.
- No streaming (SSE) support on the message endpoints.
- No response generation for `ASSISTANT`/`SYSTEM` roles — `MessageRole`
  exists as an enum so future modules can write those rows, but nothing
  currently creates them.
- No rate limiting, caching (Redis explicitly excluded), or
  background job processing.
- No database migration tool (Flyway/Liquibase) — schema is managed via
  Hibernate `ddl-auto=update`.

---

## 13. How to hand this off

Give this file to the next AI/developer along with the project source
(`ai-shopping-agent-backend/`). They should be able to:
1. Run `mvn test` to confirm the Module 1–3 baseline still passes.
2. Add new packages under `com.shoppingagent.*` for their module
   without touching `auth`, `user`, or existing `chat` code except to
   call their public service methods.
3. Extend `SecurityConfig`'s `authorizeHttpRequests` only if new public
   endpoints are needed — everything else defaults to "authenticated."
