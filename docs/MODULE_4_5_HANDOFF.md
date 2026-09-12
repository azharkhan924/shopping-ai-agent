# Module 4 & 5 — AI Shopping Brain + Product Search Engine

Implements exactly Module 4 and Module 5 from the spec, on top of the existing
Modules 1–3 described in `PROJECT_CONTEXT.md`. Modules 1–3 (auth, users,
conversations, messages) are **not touched or reproduced** here — nothing in
this drop depends on their internals beyond the documented package names.

## 1. Files added

```
com.shoppingagent.ai
├── ShoppingAiConfig.java              # ChatClient bean, default system prompt
├── ShoppingAiProperties.java          # shopping.ai.* config (context window, timeout)
├── ShoppingAiClient.java              # interface: raw LLM call -> RequirementAnalysis
├── ShoppingAiClientImpl.java          # Spring AI ChatClient-backed implementation
├── ChatTurn.java                      # lightweight (role, content) history record
├── ShoppingAgentService.java          # interface: the Module 4 entry point
├── ShoppingAgentServiceImpl.java      # prompt building, context merge, validation
├── model/ShoppingQuery.java
├── model/RequirementAnalysis.java
├── model/SortPreference.java
├── intent/ShoppingIntent.java
└── exception/AiServiceException.java, MalformedAiResponseException.java

com.shoppingagent.search
├── model/Product.java
├── model/Availability.java
├── model/ProviderSearchResult.java
├── model/SearchResult.java
├── provider/ProductSearchProvider.java
├── provider/MockProductSearchProvider.java
├── SearchOrchestrator.java
├── SearchFilter.java
├── ProductSearchService.java
└── exception/SearchException.java

com.shoppingagent.agent
├── AgentResponseStatus.java
├── AgentResponse.java
├── ShoppingChatOrchestrationService.java       # interface — wires Modules 4+5 into chat
└── ShoppingChatOrchestrationServiceImpl.java

src/test/java/com/shoppingagent/{ai,search,agent}/...   # unit + orchestration tests
```

Plus three small files describing changes to your **existing** config, meant to
be merged in by hand rather than overwritten:

- `pom-additions.xml` — Spring AI dependency + BOM
- `application-additions.yml` — `spring.ai.*` and `shopping.*` properties
- `env-additions.txt` — new `.env.example` entries

## 2. New dependency

- `org.springframework.ai:spring-ai-openai-spring-boot-starter` (via `spring-ai-bom`)

Nothing else was added — no Redis, Kafka, RabbitMQ, Elasticsearch, or paid
infra, per the constraints. The provider is swappable by changing this one
starter dependency and the corresponding `spring.ai.*` properties; none of the
`com.shoppingagent.ai` code is OpenAI-specific.

## 3. New environment variables

```
AI_API_KEY=            # your LLM provider's API key
AI_MODEL=gpt-4o-mini   # or any other chat-completion model your provider exposes
SHOPPING_SEARCH_MODE=mock
```

## 4. How this plugs into the existing chat endpoint

`ShoppingChatOrchestrationService` is the one new class your existing
`POST /api/conversations/{id}/messages` flow should call. It takes plain
`ChatTurn(role, content)` records and a `ShoppingQuery` (nullable) — **not**
your `Message`/`Conversation` entities — so it has zero compile-time coupling
to Modules 1–3. A typical call from your existing controller/service, after
you've already saved the incoming `USER` message the normal way:

```java
List<ChatTurn> history = existingMessages.stream()
        .map(m -> new ChatTurn(m.getRole().name(), m.getContent()))
        .toList();

AgentResponse response = shoppingChatOrchestrationService
        .handleUserMessage(conversation.getId().toString(), history, previousQuery, newMessageContent);

messageService.saveMessage(conversation, MessageRole.ASSISTANT, response.getMessage().getContent());
```

**Note on `previousQuery` (Module 4.6):** no new table/column was added to
persist a running `ShoppingQuery` per conversation, since that would mean
touching Modules 1–3's schema. Two easy options, your call:
1. Pass `null` every time and just widen the history window
   (`shopping.ai.max-context-turns`) — `ShoppingAgentServiceImpl` will re-derive
   the running query from the raw conversation text each call. Works fine for
   the modest history sizes this MVP has.
2. Keep the last `ShoppingQuery` in a simple in-memory `Map<UUID, ShoppingQuery>`
   in your controller/service layer for now, and pass it in. Cheap, no schema
   change, lost on restart (acceptable for a college MVP; revisit if you add a
   `shopping_queries` table later).

## 5. APIs added

No new REST endpoints. Everything above is internal service wiring meant for
your existing `POST /api/conversations/{id}/messages`. If you'd rather expose
Module 4/5 standalone for testing before wiring the endpoint, you can trivially
add a temporary controller that calls `shoppingChatOrchestrationService`
directly — not included here since the spec said keep this backend-internal.

## 6. Running in mock mode

Mock mode is the default (`SHOPPING_SEARCH_MODE=mock` or unset). No product API
key or network access needed for search — `MockProductSearchProvider` returns
demo data for pendrives and headphones (SanDisk/Kingston/HP/Samsung,
boAt/JBL/Sony/Noise). You still need a real `AI_API_KEY`/`AI_MODEL` for Module 4
(natural-language understanding) — there's no mock LLM in production wiring,
but tests mock `ShoppingAiClient`/`ShoppingAgentService` directly so `mvn test`
needs no API key.

## 7. Example request/response (conceptual — via the orchestration service)

Request (latest user message): `"Mujhe 128GB ka pendrive chahiye achhi brand ka, ₹1000 ke andar."`

```json
{
  "conversationId": "b3b7...",
  "message": { "role": "ASSISTANT", "content": "I found 3 options matching your requirements." },
  "status": "PRODUCT_RESULTS",
  "products": [
    { "id": "p1", "name": "SanDisk Ultra Flair 128GB", "brand": "SanDisk", "price": 899, "currency": "INR",
      "rating": 4.5, "reviewCount": 12000, "store": "Amazon", "productUrl": "https://example.com/product/p1",
      "source": "MOCK" },
    { "id": "p2", "name": "Kingston DataTraveler 128GB", "brand": "Kingston", "price": 849, "currency": "INR",
      "rating": 4.4, "reviewCount": 8500, "store": "Flipkart", "productUrl": "https://example.com/product/p2",
      "source": "MOCK" },
    { "id": "p3", "name": "HP x796w 128GB", "brand": "HP", "price": 799, "currency": "INR",
      "rating": 4.3, "reviewCount": 4200, "store": "Croma", "productUrl": "https://example.com/product/p3",
      "source": "MOCK" }
  ]
}
```

Under-specified request: `"I want headphones."`

```json
{
  "conversationId": "b3b7...",
  "message": { "role": "ASSISTANT", "content": "What's your approximate budget?" },
  "status": "CLARIFICATION",
  "products": []
}
```

## 8. Error handling implemented

- AI unavailable / times out / malformed JSON → `AiServiceException` (or its
  `MalformedAiResponseException` subtype) inside `ShoppingAgentServiceImpl`,
  caught by the orchestration service → `status: ERROR`, generic message, no
  stack trace, no internal exception text leaked to the user.
- One search provider failing → isolated in `SearchOrchestrator`, logged,
  excluded from results; the rest of the search proceeds normally.
- All search providers failing → `SearchException` → `status: ERROR` with the
  exact message from the spec ("I couldn't search for products right now...").
- No products after filtering → `status: NO_RESULTS`, not an error.
- Unknown/blank fields never crash filtering — `SearchFilter` treats an unknown
  spec/price/rating as "don't exclude", per spec 5.7.

## 9. Tests included (all mock the AI layer — no paid API required)

- `ShoppingAgentServiceImplTest` — simple query, query with budget, query with
  brand, multiple requirements (the Hinglish pendrive example), missing info →
  clarification, refinement merges previous query into the prompt, malformed
  AI output (missing intent / missing shoppingQuery) throws, blank input
  rejected, history windowing truncates to the most recent N turns.
- `MockProductSearchProviderTest` — returns demo products, tags them `MOCK`.
- `SearchFilterTest` — price filtering, category filtering, brand
  inclusion/exclusion, unknown fields don't wrongly exclude.
- `SearchOrchestratorAndServiceTest` — multiple providers combine, one provider
  failing doesn't break the search, all providers failing → `SearchException`,
  no-results case, end-to-end price filtering through `ProductSearchService`.
- `ShoppingChatOrchestrationServiceImplTest` — the full
  clarification / product-results / no-results / error / unsupported-comparison
  flows, simulating what the message endpoint will see.

## 10. Not run against a real Maven build

This sandbox has no network access to Maven Central, so `mvn test` could not
actually be executed here (same limitation noted in `PROJECT_CONTEXT.md` §10
for the original Module 1–3 code). Please run `mvn test` locally after merging
these files in — the code compiles against Spring Boot 3.3.4 / Java 21 /
Lombok / Jackson, which your project already has, plus the one new Spring AI
dependency above.

## 11. Intentionally NOT implemented (per spec)

- Module 6 (product normalization / duplicate detection)
- Module 7 (comparison & ranking) — `PRODUCT_COMPARISON` intent is detected but
  currently answered with a "not available yet" clarification rather than a
  real comparison
- Module 8 (complete intelligent agent pipeline)
- Module 9 (SSE streaming)
- Persisting `ShoppingQuery` across turns in the database (see §4 note)
- Redis/Kafka/RabbitMQ/Elasticsearch, microservices, real shopping-site
  scraping, any mandatory paid API
