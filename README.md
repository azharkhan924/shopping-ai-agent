# Penny — AI Shopping Agent (Full-Stack Spring Boot + Vanilla Web)

Penny is an intelligent, full-stack AI shopping assistant that understands conversational buyer requests, searches live e-commerce products (Amazon, Flipkart, Croma, Reliance Digital), normalizes product data, eliminates duplicates across multiple merchants, deterministically scores and ranks options, and streams real-time recommendations via Server-Sent Events (SSE) with voice input and speech synthesis.

![Penny AI Shopping Agent](src/main/resources/static/images/screenshot.png)

---

## 🌟 Key Features

- **Conversational AI Brain (Module 4)**: Natural language intent classification and shopping requirement extraction powered by Groq / Spring AI (`openai/gpt-oss-120b`). Fluent in English, Hindi, and Hinglish.
- **Live Real-World Product Search (Module 5)**: Multi-store product discovery across Amazon.in, Flipkart, Croma, and Reliance Digital with authentic pricing, high-resolution imagery, and direct buy links. Includes a zero-dependency mock catalog fallback.
- **Product Normalization & Multi-Store Grouping (Module 6)**: Brand/spec standardization and Jaccard-similarity duplicate detection consolidating multiple merchant offers into clean product groups.
- **Multi-Dimensional Product Ranking & Recommendation Badges (Module 7)**: Deterministic scoring formula (Price 30%, Rating 25%, Reviews 15%, Specs 15%, Value 15%) and smart badges (`BEST_OVERALL`, `BEST_VALUE`, `CHEAPEST`, `HIGHEST_RATED`, `BEST_BATTERY`).
- **Complete Shopping Agent Pipeline (Module 8)**: Multi-turn refinement memory, contextual budget management, and structured response synthesis.
- **Real-Time SSE Streaming (Module 9)**: Real-time pipeline stage telemetry (`ANALYZING` → `SEARCHING` → `NORMALIZING` → `COMPARING` → `RANKING` → `GENERATING_RESPONSE`) streamed over Server-Sent Events (`POST /api/chat/stream`).
- **Voice-Enabled Interface**: Full speech-to-text voice input via Web Speech Recognition API and hands-free text-to-speech "Read Aloud" reading of recommendations.
- **Production Polish & Security (Modules 2 & 10)**: Stateless JWT authentication (jjwt 0.12), BCrypt password hashing, Spring Security 6, global exception handling, and comprehensive validation.
- **Warm & Modern UI**: Built with pure Vanilla HTML5, CSS3, and JavaScript — zero heavy build chains, no React/Tailwind needed.

---

## 🏗️ Architecture & Modules

```
com.shoppingagent
├── ShoppingAgentApplication.java
├── agent/                     # Module 8 & 9: Pipeline Orchestration & SSE Chat API
│   ├── ShoppingAgentPipelineService.java
│   └── ChatController.java     # POST /api/chat, POST /api/chat/stream
├── ai/                        # Module 4: LLM Shopping Brain & Heuristics
│   ├── ShoppingAgentServiceImpl.java
│   └── ShoppingAiClientImpl.java
├── auth/                      # Module 2: Authentication (Register / Login / JWT)
│   ├── AuthController.java
│   └── AuthService.java
├── chat/                      # Module 3: Conversation & Message History
│   ├── conversation/
│   └── message/
├── product/                   # Modules 6 & 7: Normalization, Dedup, Comparison & Ranking
│   ├── comparison/            # Side-by-side comparison tables
│   ├── dedup/                 # Jaccard similarity duplicate detection
│   ├── grouping/              # Multi-store product grouping
│   ├── normalization/         # Brand/spec normalization
│   └── ranking/               # Deterministic scoring & badge assignment
├── search/                    # Module 5: Search Orchestrator, Filters & Providers
│   ├── SearchFilter.java      # Spec substring matching & category synonyms
│   ├── SearchOrchestrator.java
│   ├── ProductSearchService.java
│   └── provider/
│       ├── LiveProductSearchProvider.java  # Real-world e-commerce discovery
│       └── MockProductSearchProvider.java  # Offline mock catalog
├── security/                  # Spring Security 6 & JWT Filter Chain
├── config/                    # SecurityConfig & CORS configuration
├── exception/                 # GlobalExceptionHandler & ErrorResponse DTOs
└── user/                      # User entity & profile management
```

---

## 🚀 Quick Start

### 1. Prerequisites
- **Java 21**
- **Maven 3.8+**
- (Optional) **Groq API Key** for live AI search mode

### 2. Configuration
Copy the environment template:
```bash
cp .env.example .env
```
Edit `.env` or set environment variables:
```env
SERVER_PORT=8081
SPRING_AI_OPENAI_API_KEY=your-groq-api-key-here
SHOPPING_SEARCH_MODE=live
```
*(Note: If no API key is provided, the application automatically falls back to the built-in Mock catalog seamlessly!)*

### 3. Build & Run
```bash
mvn clean compile
mvn spring-boot:run
```

The application will start at **http://localhost:8081**.
Open your browser to start chatting with Penny!

---

## 🧪 Testing

The repository includes a comprehensive unit and integration test suite covering all 10 modules:

```bash
mvn test
```

**Test Suite Coverage (65 passing tests):**
- Auth & JWT Security: 7 tests
- Conversation & Messages: 7 tests
- User & Profile Management: 2 tests
- Normalization & Similarity Deduplication: 4 tests
- Multi-Store Grouping & Comparison: 3 tests
- Scoring, Ranking & Badge Assignment: 2 tests
- Search Orchestration, Mock & Live Providers: 12 tests
- Search Filtering & Spec Substring Matching: 7 tests
- Shopping Agent Pipeline & Fallback Heuristics: 8 tests
- Real-time SSE Chat Controller: 2 tests
- Health & Diagnostic Endpoints: 1 test

---

## 📡 API Endpoints

### Authentication
- `POST /api/auth/register` — Register new user account
- `POST /api/auth/login` — Login & receive JWT access token
- `GET /api/auth/me` — Get current user profile

### Conversations & Chat
- `GET /api/conversations` — List all conversations for authenticated user
- `POST /api/conversations` — Create new conversation thread
- `DELETE /api/conversations/{id}` — Delete conversation thread
- `POST /api/chat` — Synchronous chat endpoint returning ranked products
- `POST /api/chat/stream` — Real-time Server-Sent Events (SSE) streaming endpoint

### System & Health
- `GET /api/health` — Service status, active search mode, and version

---

## 📜 License
MIT License
