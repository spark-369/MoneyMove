# MoneyMove — Money Movement Platform

A full-stack **money movement demo** built for the **PSTU IT Carnival 2026**. MoneyMove lets users
create an account, receive an initial balance, send peer-to-peer transfers, raise money requests
("please pay me"), accept or decline those requests, and review a complete transaction history.

The project is a conventional two-tier application:

| Tier | Stack | Location |
| --- | --- | --- |
| **Backend** | Java 17 · Spring Boot 3.4.5 · Spring Security (JWT) · Spring Data JPA · PostgreSQL · Redis · Maven | `backend/` |
| **Frontend** | React 19 · Vite · React Router 7 · Vanilla CSS design system | `frontend/` |

---

## Table of Contents

1. [Features](#features)
2. [Architecture Overview](#architecture-overview)
3. [Repository Layout](#repository-layout)
4. [Quick Start](#quick-start)
5. [Backend Documentation](#backend-documentation)
   - [Technology & Dependencies](#backend-technology--dependencies)
   - [Configuration](#backend-configuration)
   - [Package Structure](#backend-package-structure)
   - [Domain Model](#domain-model)
   - [Security Model](#security-model)
   - [REST API Reference](#rest-api-reference)
   - [Request / Response DTOs](#request--response-dtos)
   - [Error Handling](#error-handling)
   - [Concurrency, Idempotency & Consistency](#concurrency-idempotency--consistency)
   - [Caching](#caching)
   - [Observability & API Docs](#observability--api-docs)
   - [Testing](#backend-testing)
6. [Frontend Documentation](#frontend-documentation)
   - [Technology & Dependencies](#frontend-technology--dependencies)
   - [Configuration](#frontend-configuration)
   - [Project Structure](#frontend-project-structure)
   - [Routing & Route Guards](#routing--route-guards)
   - [State & Session Management](#state--session-management)
   - [API Client Layer](#api-client-layer)
   - [Pages](#pages)
   - [Styling & Theming](#styling--theming)
   - [Utilities](#utilities)
7. [End-to-End Flows](#end-to-end-flows)
8. [Production Hardening Notes](#production-hardening-notes)

---

## Features

- **Authentication** — register and login with username/password, BCrypt-hashed passwords, stateless
  JWT bearer tokens (24 h expiry).
- **Accounts** — every new user is automatically provisioned an account seeded with a configurable
  initial balance (default `100000 BDT`) recorded as an `INITIAL_FUNDING` transaction.
- **Peer-to-peer transfers** — send money to any user by username; self-transfers and
  non-positive amounts are rejected, and insufficient funds are surfaced as a clear error.
- **Money requests** — request money from another user; the debtor can **accept** (which settles the
  request as a real transfer) or **decline** it. Incoming and outgoing pending requests are listed
  separately.
- **Transaction history** — full ledger of transfers, request settlements, and initial funding, with
  resolved sender/receiver usernames.
- **User search** — type-ahead search over usernames and full names (minimum 2 characters, top 10).
- **Balance caching** — balances are cached in Redis (60 s TTL) and evicted on any money movement.
- **OpenAPI / Swagger UI** and **Spring Boot Actuator** health & metrics endpoints.
- **Polished UI** — responsive React SPA with a dark/light theme toggle, skeletons, toasts and
  status badges.

---

## Architecture Overview

```
┌──────────────────────────────┐        HTTPS / JSON         ┌───────────────────────────────────┐
│        React SPA             │  ───────────────────────▶   │        Spring Boot API            │
│  (Vite dev server :5173)     │   Authorization: Bearer     │        (Tomcat :8080)             │
│                              │  ◀───────────────────────   │                                   │
│  • AuthContext (JWT)         │                             │  Controllers → Services → Repos   │
│  • fetch-based api/ client   │                             │  JWT filter · BCrypt · CORS       │
│  • react-router-dom routes   │                             │                                   │
└──────────────────────────────┘                             └───────────────┬───────────────────┘
                                                                             │
                                                     ┌───────────────────────┼───────────────────────┐
                                                     ▼                       ▼                       ▼
                                              ┌─────────────┐        ┌──────────────┐       ┌──────────────┐
                                              │ PostgreSQL  │        │    Redis     │       │  Swagger UI  │
                                              │ :5432       │        │    :6379     │       │  /actuator   │
                                              │ moneyapp DB │        │ balance cache│       │              │
                                              └─────────────┘        └──────────────┘       └──────────────┘
```

The backend is a classic layered Spring application (Controller → Service → Repository) with stateless
JWT authentication. The frontend is a single-page React application that talks exclusively to the
JSON REST API and stores its session token in `localStorage`.

---

## Repository Layout

```
v1/
├── README.md                      ← this document
├── backend/                       Spring Boot application
│   ├── mvnw / mvnw.cmd            Maven wrapper
│   ├── pom.xml                    Maven build (Spring Boot 3.4.5, Java 17)
│   └── src/
│       ├── main/
│       │   ├── java/com/pstu/backend/
│       │   │   ├── BackendApplication.java
│       │   │   ├── config/CacheConfig.java
│       │   │   ├── controller/     REST controllers
│       │   │   ├── domain/         JPA entities + enums
│       │   │   ├── dto/request/    Inbound payloads
│       │   │   ├── dto/response/   Outbound payloads
│       │   │   ├── exception/      Exceptions + global handler
│       │   │   ├── mapper/         Entity → DTO mappers
│       │   │   ├── repository/     Spring Data JPA repositories
│       │   │   ├── security/       JWT, security config, helpers
│       │   │   └── service/        Business logic
│       │   └── resources/application.properties
│       └── test/
│           ├── java/com/pstu/backend/…    Unit + concurrency tests
│           └── resources/application.properties   H2 test profile
└── frontend/                      React SPA
    ├── package.json               npm scripts & dependencies
    ├── vite.config.js
    ├── eslint.config.js
    ├── index.html                 SPA entry document
    └── src/
        ├── main.jsx               React bootstrap
        ├── App.jsx                Router + route table
        ├── index.css              Design system (raw CSS)
        ├── api/                   fetch client + endpoint wrappers
        ├── components/            Layout, ProtectedRoute
        ├── context/               AuthContext, useAuth, authContext
        ├── pages/                 Login, Register, Dashboard, Transfer, Requests, History, 404
        └── utils/format.js        Money / date / badge helpers
```

---

## Quick Start

### Prerequisites

| Tool | Version |
| --- | --- |
| JDK | 17+ |
| Maven | bundled via `./mvnw` |
| Node.js | 18+ (20+ recommended) |
| PostgreSQL | 14+ (database `moneyapp`) |
| Redis | 6+ (optional for dev — see notes) |

### 1. Start PostgreSQL and Redis

```bash
# Example with Docker
docker run -d --name moneyapp-db  -e POSTGRES_PASSWORD=mysecretpassword \
  -e POSTGRES_DB=moneyapp -p 5432:5432 postgres:16

docker run -d --name moneyapp-redis -p 6379:6379 redis:7
```

### 2. Run the backend

```bash
cd backend
./mvnw spring-boot:run
# API      → http://localhost:8080
# Swagger  → http://localhost:8080/swagger-ui.html
# Health   → http://localhost:8080/actuator/health
```

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
# UI → http://localhost:5173
```

> The frontend defaults to `http://localhost:8080/api` for API calls. Override it by creating
> `frontend/.env.local` with `VITE_API_BASE_URL=https://your-api.example.com/api`.

---

## Backend Documentation

### Backend Technology & Dependencies

| Concern | Choice |
| --- | --- |
| Language / Runtime | Java 17 |
| Framework | Spring Boot 3.4.5 |
| Web | `spring-boot-starter-web` (Spring MVC + embedded Tomcat) |
| Persistence | `spring-boot-starter-data-jpa` (Hibernate) |
| Database | PostgreSQL (`postgresql` driver, runtime) |
| Cache | `spring-boot-starter-data-redis` + `starter-cache` (Redis) |
| Security | `spring-boot-starter-security` + JJWT 0.12.6 |
| Validation | `spring-boot-starter-validation` (Jakarta Bean Validation) |
| API Docs | `springdoc-openapi-starter-webmvc-ui` 2.7.0 |
| Observability | `spring-boot-starter-actuator` |
| Boilerplate | Lombok |
| Testing | Spring Boot Test, Spring Security Test, H2, Mockito, Testcontainers (PostgreSQL) |

### Backend Configuration

`backend/src/main/resources/application.properties`:

| Property | Value | Purpose |
| --- | --- | --- |
| `server.port` | `8080` | HTTP port |
| `server.error.include-message` | `always` | Include error messages in default error payloads |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/moneyapp` | PostgreSQL connection |
| `spring.datasource.username` / `password` | `postgres` / `mysecretpassword` | DB credentials |
| `spring.datasource.hikari.maximum-pool-size` / `minimum-idle` | `20` / `5` | Connection pool sizing |
| `spring.jpa.hibernate.ddl-auto` | `update` | Auto-manage schema (dev convenience) |
| `spring.jpa.open-in-view` | `false` | Avoid lazy-loading in the view layer |
| `spring.jpa.properties.hibernate.jdbc.batch_size` | `50` | JDBC batch size |
| `spring.jpa.properties.hibernate.order_inserts/updates` | `true` | Batch ordering optimization |
| `spring.data.redis.host` / `port` | `localhost` / `6379` | Redis connection |
| `spring.data.redis.timeout` | `2000ms` | Command timeout |
| `spring.data.redis.lettuce.pool.max-active/max-idle` | `16` / `8` | Redis connection pool |
| `spring.cache.type` | `redis` | Cache provider |
| `spring.cache.redis.time-to-live` | `60s` | Default cache TTL |
| `spring.jackson.default-property-inclusion` | `non-null` | Omit null fields from JSON |
| `jwt.secret` | `${JWT_SECRET:change-me-…}` | HMAC signing key (**override in production**) |
| `jwt.expiration-ms` | `86400000` | Token lifetime (24 h) |
| `app.initial-balance` | `100000` | Welcome balance for new accounts |
| `app.currency` | `BDT` | Account currency |
| `management.endpoints.web.exposure.include` | `health,info,metrics` | Actuator endpoints |
| `management.endpoint.health.show-details` | `never` | Do not leak health internals |

**Secrets.** `jwt.secret` reads the `JWT_SECRET` environment variable, falling back to a placeholder.
Always set a strong, 256-bit+ random value in production:

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
```

### Backend Package Structure

All code lives under `com.pstu.backend`:

| Package | Responsibility |
| --- | --- |
| `BackendApplication` | Spring Boot entry point |
| `config` | `CacheConfig` — Redis cache manager with JSON (de)serialization |
| `controller` | REST endpoints (`AuthController`, `AccountController`, `TransferController`, `MoneyRequestController`) |
| `domain` | JPA entities and enums (`User`, `Account`, `Transaction`, `MoneyRequest`, `TransactionType`, `TransactionStatus`, `MoneyRequestStatus`) |
| `dto.request` | Validated inbound payloads |
| `dto.response` | Outbound payloads |
| `exception` | Custom exceptions + `GlobalExceptionHandler` (`@RestControllerAdvice`) |
| `mapper` | `TransactionMapper` — entity → DTO conversion |
| `repository` | Spring Data JPA repositories |
| `security` | `SecurityConfig`, `JwtUtil`, `JwtAuthenticationFilter`, `CustomUserDetailsService`, `SecurityHelper` |
| `service` | Business logic (`AuthService`, `AccountService`, `TransferService`, `MoneyRequestService`) |

### Domain Model

**`users` — `User`**

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `Long` | PK, identity |
| `username` | `String(50)` | unique, not null |
| `email` | `String(120)` | unique, not null |
| `passwordHash` | `String` | BCrypt hash, not null |
| `fullName` | `String(100)` | not null |
| `phoneNumber` | `String(20)` | optional |
| `createdAt` | `Instant` | defaults to now |

**`accounts` — `Account`**

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `Long` | PK, identity |
| `user` | `User` | `@OneToOne` lazy, `user_id` unique, not null |
| `balance` | `BigDecimal(19,2)` | not null |
| `currency` | `String(3)` | not null |
| `updatedAt` | `Instant` | touched on every debit/credit |
| `version` | `Long` | `@Version` — optimistic locking |

Domain methods: `debit(amount)` and `credit(amount)` mutate the balance and refresh `updatedAt`.

**`transactions` — `Transaction`** (indexed on `referenceId`, `senderAccountId`, `receiverAccountId`)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `Long` | PK, identity |
| `referenceId` | `String(36)` | unique UUID, generated in the constructor |
| `senderAccountId` / `receiverAccountId` | `Long` | **account** ids (not user ids) |
| `amount` | `BigDecimal(19,2)` | not null |
| `currency` | `String(3)` | not null |
| `type` | enum | `TRANSFER`, `REQUEST_SETTLEMENT`, `INITIAL_FUNDING` |
| `status` | enum | `PENDING`, `COMPLETED`, `FAILED`, `REVERSED` |
| `description` | `String(255)` | optional |
| `idempotencyKey` | `String(100)` | optional; used to deduplicate retries |
| `createdAt` | `Instant` | defaults to now |

**`money_requests` — `MoneyRequest`** (indexed on `requesterAccountId`, `debtorAccountId`)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `Long` | PK, identity |
| `requesterAccountId` / `debtorAccountId` | `Long` | account ids |
| `amount` | `BigDecimal(19,2)` | not null |
| `currency` | `String(3)` | not null |
| `note` | `String(255)` | optional |
| `status` | enum | `PENDING`, `ACCEPTED`, `DECLINED`, `CANCELLED`, `EXPIRED` |
| `settledTransactionId` | `Long` | set when accepted; links to the settling transaction |
| `createdAt` / `resolvedAt` | `Instant` | creation + resolution timestamps |

### Security Model

- **Stateless sessions** — `SessionCreationPolicy.STATELESS`; no HTTP session is created.
- **Password hashing** — `BCryptPasswordEncoder`.
- **JWT** — HMAC-SHA signed with `jwt.secret`; subject = username; 24 h expiry. Created by `JwtUtil`.
- **Authentication filter** — `JwtAuthenticationFilter` (a `OncePerRequestFilter`) reads the
  `Authorization: Bearer <token>` header, extracts the username, loads `UserDetails` via
  `CustomUserDetailsService`, validates the token, and populates the `SecurityContext`.
- **Authorization rules** (`SecurityConfig`):
  - Public: `/api/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/swagger-ui.html`, `/actuator/health`
  - Everything else requires authentication.
- **CORS** — all origin patterns, all methods, all headers, credentials allowed.
- **Current principal** — `SecurityHelper.currentUsername()` resolves the authenticated username from
  the `SecurityContext`; services use it for ownership checks and to identify the acting user.

### REST API Reference

Base path: `/api`. All endpoints except `/auth/**` require the header
`Authorization: Bearer <jwt>`.

#### Authentication — `/api/auth`

| Method | Path | Auth | Body | Returns |
| --- | --- | --- | --- | --- |
| `POST` | `/api/auth/register` | ❌ | `RegisterRequest` | `AuthResponse` |
| `POST` | `/api/auth/login` | ❌ | `LoginRequest` | `AuthResponse` |

Registering creates the `User`, an `Account` funded with `app.initial-balance`, and an
`INITIAL_FUNDING` transaction, then returns a token so the client is logged in immediately.

#### Account — `/api/account`

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| `GET` | `/api/account/me/balance` | ✅ | Balance of the caller |
| `GET` | `/api/account/balance/{username}` | ✅ | Only the caller's own username is allowed; otherwise `403` |
| `GET` | `/api/account/me/history` | ✅ | Caller's transactions, newest first |
| `GET` | `/api/account/users/search?q=` | ✅ | Type-ahead; requires ≥ 2 chars; top 10 |

#### Transfers — `/api/transfers`

| Method | Path | Auth | Body / Notes |
| --- | --- | --- | --- |
| `POST` | `/api/transfers` | ✅ | `TransferRequest` → `TransactionResponse` |
| `GET` | `/api/transfers/{referenceId}` | ✅ | Look up a transaction by its reference UUID |

#### Money Requests — `/api/requests`

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| `POST` | `/api/requests` | ✅ | Create a `PENDING` request (`MoneyRequestCreateRequest`) |
| `POST` | `/api/requests/{id}/accept` | ✅ | Debtor pays; settles as a `REQUEST_SETTLEMENT` transfer → `TransactionResponse` |
| `POST` | `/api/requests/{id}/decline` | ✅ | Debtor declines → `MoneyRequestResponse` |
| `GET` | `/api/requests/incoming` | ✅ | Pending requests where the caller is the debtor |
| `GET` | `/api/requests/outgoing` | ✅ | Pending requests where the caller is the requester |

**Example — register**

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
        "username": "alice",
        "email": "alice@example.com",
        "password": "secret123",
        "fullName": "Alice Khan",
        "phoneNumber": "01700000000"
      }'
```

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "alice",
  "email": "alice@example.com",
  "fullName": "Alice Khan",
  "balance": 100000.00,
  "currency": "BDT"
}
```

**Example — transfer**

```bash
curl -X POST http://localhost:8080/api/transfers \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{ "toUsername": "bob", "amount": "250.00",
        "description": "Dinner split",
        "idempotencyKey": "8f1c0f4e-…" }'
```

```json
{
  "referenceId": "b1f2c3d4-…",
  "type": "TRANSFER",
  "status": "COMPLETED",
  "senderAccountId": 1,
  "receiverAccountId": 2,
  "senderUsername": "alice",
  "receiverUsername": "bob",
  "amount": 250.00,
  "currency": "BDT",
  "description": "Dinner split",
  "createdAt": "2026-01-01T12:00:00Z"
}
```

### Request / Response DTOs

**Requests** (`dto.request`)

| DTO | Field | Validation |
| --- | --- | --- |
| `RegisterRequest` | `username` | `@NotBlank`, `@Size(3..50)` |
| | `email` | `@NotBlank`, `@Email` |
| | `password` | `@NotBlank`, `@Size(8..128)` |
| | `fullName` | `@NotBlank`, `@Size(1..100)` |
| | `phoneNumber` | `@Pattern(^[0-9+\- ]{6,20}$)`, optional |
| `LoginRequest` | `username`, `password` | `@NotBlank` |
| `TransferRequest` | `toUsername` | `@NotBlank` |
| | `amount` | `@NotNull`, `@DecimalMin("0.01")` |
| | `description` | `@Size(max 255)`, optional |
| | `idempotencyKey` | optional |
| `MoneyRequestCreateRequest` | `fromUsername` | `@NotBlank` |
| | `amount` | `@NotNull`, `@DecimalMin("0.01")` |
| | `note` | `@Size(max 255)`, optional |

**Responses** (`dto.response`)

| DTO | Fields |
| --- | --- |
| `AuthResponse` | `token`, `username`, `email`, `fullName`, `balance`, `currency` |
| `BalanceResponse` | `balance`, `currency` |
| `TransactionResponse` | `referenceId`, `type`, `status`, `senderAccountId`, `receiverAccountId`, `senderUsername`, `receiverUsername`, `amount`, `currency`, `description`, `createdAt` |
| `MoneyRequestResponse` | `id`, `requesterAccountId`, `debtorAccountId`, `requesterUsername`, `debtorUsername`, `amount`, `currency`, `note`, `status`, `createdAt`, `resolvedAt` |
| `ApiError` | `status`, `error`, `message`, `timestamp` |

`TransactionMapper` is the single source of truth that converts a `Transaction` (which stores
*account* ids) into a `TransactionResponse`, resolving usernames via
`UserRepository.findByAccountId`.

### Error Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to consistent HTTP responses:

| Exception | Status | Error |
| --- | --- | --- |
| `ResourceNotFoundException` | `404` | Not Found |
| `InsufficientBalanceException` | `400` | Insufficient Balance |
| `BadRequestException` | `400` | Bad Request |
| `ConflictException` | `409` | Conflict |
| `ForbiddenException` | `403` | Forbidden |
| `BadCredentialsException` | `401` | Unauthorized |
| `MethodArgumentNotValidException` | `400` | Validation Failed (field → message map) |
| `PessimisticLockException` | `409` | Concurrency Conflict |
| `OptimisticLockException` | `409` | Concurrency Conflict |
| `IllegalArgumentException` | `400` | Bad Request |
| `Exception` (catch-all) | `500` | Internal Server Error |

Standard error body:

```json
{ "status": 404, "error": "Not Found", "message": "Recipient not found: ghost", "timestamp": "2026-01-01T12:00:00Z" }
```

Validation error body nests a field→message map under `message`.

### Concurrency, Idempotency & Consistency

Money movement is the critical path, so the services apply several safeguards:

- **Serializable isolation** — `TransferService.transfer` and `MoneyRequestService.acceptRequest`
  run at `Isolation.SERIALIZABLE`.
- **Pessimistic locking** — accounts involved in a movement are loaded with
  `findByUserIdForUpdate` / `findByIdForUpdate` (`SELECT … FOR UPDATE`), preventing lost updates
  and double-spends.
- **Optimistic locking** — `Account.version` (`@Version`) provides a second safety net.
- **Idempotency** — `TransferRequest.idempotencyKey` is looked up before processing; a replay of the
  same key returns the original transaction instead of moving money twice. The frontend generates a
  per-form key and rotates it only after a successful transfer.
- **Atomicity** — every money movement is a single `@Transactional` unit: debit, credit, transaction
  record, and (for request settlement) status update all commit or roll back together.
- **Rounding** — amounts are normalized to 2 decimals with `RoundingMode.HALF_UP`.
- **Conservation** — both transfer paths assert debit == credit, so total money is preserved (proven
  by `TransferServiceConcurrencyTest`).

### Caching

- `CacheConfig` builds a `RedisCacheManager` with a 60 s TTL and JSON serialization
  (`Jackson2JsonRedisSerializer<BalanceResponse>`).
- `AccountService.getBalance` is `@Cacheable(cacheNames = "balances", key = "#username")`.
- `TransferService.transfer` and `MoneyRequestService.acceptRequest` are
  `@CacheEvict(cacheNames = "balances", allEntries = true)`, so balances are invalidated the moment
  money moves.

> Caching is Redis-only. If Redis is unavailable, start the app with `spring.cache.type=none`
> (or run the tests, which use `spring.cache.type=none`) to avoid cache calls.

### Observability & API Docs

| Endpoint | URL |
| --- | --- |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Health | `http://localhost:8080/actuator/health` |
| Info | `http://localhost:8080/actuator/info` |
| Metrics | `http://localhost:8080/actuator/metrics` |

### Backend Testing

Tests run against **H2 in PostgreSQL-compatibility mode** (`TEST` profile →
`backend/src/test/resources/application.properties`), with `ddl-auto=create-drop` and caching
disabled. Testcontainers (PostgreSQL) is available on the classpath for integration-style tests.

| Test | Scope |
| --- | --- |
| `BackendApplicationTests` | Context loads (`@SpringBootTest`) |
| `AuthServiceTest` | Register/login, duplicate username/email, bad credentials, missing user |
| `AccountServiceTest` | Balance lookup, history, username resolution, user search rules |
| `TransferServiceTest` | Happy path, self-transfer, unknown recipient, insufficient balance, rounding, idempotency |
| `TransferServiceConcurrencyTest` | 20-thread concurrent transfers — no overdraw, conservation of money; replay of an idempotency key does not double-spend |
| `MoneyRequestServiceTest` | Create/accept/decline, authorization, already-resolved, insufficient balance, listing |
| `JwtUtilTest` | Token generation/validation, wrong user, expiry, tampering |

Run the suite:

```bash
cd backend
./mvnw test
```

---

## Frontend Documentation

### Frontend Technology & Dependencies

| Concern | Choice |
| --- | --- |
| UI library | React 19 |
| Build tool | Vite 8 |
| Routing | `react-router-dom` 7 |
| Linting | ESLint 10 with `eslint-plugin-react-hooks` and `eslint-plugin-react-refresh` |
| Styling | Hand-written CSS design system (`src/index.css`) with CSS variables and light/dark themes |

**npm scripts**

| Script | Command | Purpose |
| --- | --- | --- |
| `dev` | `vite` | Start the dev server (default `http://localhost:5173`) |
| `build` | `vite build` | Produce a production bundle in `dist/` |
| `preview` | `vite preview` | Serve the built bundle locally |
| `lint` | `eslint .` | Static analysis |

### Frontend Configuration

- **API base URL** — `src/api/client.js` reads `import.meta.env.VITE_API_BASE_URL`, defaulting to
  `http://localhost:8080/api`. Override via a `.env` / `.env.local` file:

  ```bash
  # frontend/.env.local
  VITE_API_BASE_URL=http://localhost:8080/api
  ```

- **localStorage keys** — `mm_token` (JWT) and `mm_user` (cached profile JSON); theme persisted under
  `mm_theme`.

### Frontend Project Structure

```
frontend/src/
├── main.jsx                 React root (StrictMode)
├── App.jsx                  Router, AuthProvider, route table
├── index.css                Design system: tokens, layout, components
├── api/
│   ├── client.js            fetch wrapper, token storage, ApiError
│   ├── auth.js              register / login
│   ├── account.js           balance, history, user search
│   ├── transfer.js          transfer, lookup by reference
│   └── requests.js          create / accept / decline / list requests
├── components/
│   ├── Layout.jsx           Top bar, nav, theme toggle, user chip, footer
│   └── ProtectedRoute.jsx   Auth gate + initializing skeleton
├── context/
│   ├── AuthContext.jsx      AuthProvider (state + actions)
│   ├── authContext.js       createContext(null)
│   └── useAuth.js           useAuth() hook with provider guard
├── pages/
│   ├── LoginPage.jsx
│   ├── RegisterPage.jsx
│   ├── DashboardPage.jsx
│   ├── TransferPage.jsx
│   ├── RequestsPage.jsx
│   ├── HistoryPage.jsx
│   └── NotFoundPage.jsx
└── utils/format.js          generateIdempotencyKey, formatMoney, formatDate, statusBadgeClass
```

### Routing & Route Guards

`App.jsx` wires `AuthProvider` → `BrowserRouter` → `Layout` → `Routes`:

| Path | Page | Access |
| --- | --- | --- |
| `/login` | `LoginPage` | Public (redirects home if authenticated) |
| `/register` | `RegisterPage` | Public (redirects home if authenticated) |
| `/` | `DashboardPage` | Protected |
| `/transfer` | `TransferPage` | Protected |
| `/requests` | `RequestsPage` | Protected |
| `/history` | `HistoryPage` | Protected |
| `/404` | `NotFoundPage` | Public |
| `*` | redirect to `/404` | — |

`ProtectedRoute` renders a skeleton while `initializing` (a stored token is being verified), redirects
to `/login` (preserving the intended location in router state) when unauthenticated, and otherwise
renders its children.

### State & Session Management

`AuthProvider` (`context/AuthContext.jsx`) owns the session:

- **Bootstrapping** — initializes from `localStorage`. If a token exists but no cached profile does,
  `initializing` is `true` and it calls `getMyBalance()` to verify the session and recover the profile.
  `401`s clear the session; transient/network errors keep the token (the user is not logged out for a
  cold backend).
- **Actions** — `login`, `register` (both persist the returned token + profile via `applyAuthResponse`),
  `logout`, and `updateBalance(balance, currency)` for optimistic balance refresh after money movement.
- **Exposed value** — `{ user, token, loading, initializing, isAuthenticated, login, register, logout,
  updateBalance }`, memoized with `useMemo`.

`useAuth()` throws if used outside the provider, guarding against misuse. Theme state lives in
`Layout.jsx` (`useTheme`), toggling `data-theme` on `<html>`.

### API Client Layer

`api/client.js` is a thin `fetch` wrapper:

- Builds URLs from `API_BASE_URL`, appends query params, and serializes JSON bodies.
- `buildHeaders(requireAuth)` adds `Content-Type: application/json` and, when required, the
  `Authorization: Bearer <token>` header.
- Defines `ApiError { message, status, data }`; network failures surface as status `0`.
- On `401` responses it clears the stored token and user.
- `extractErrorMessage` flattens backend payloads — including validation maps — into a readable string.
- Exposes `api.get/post/put/patch/del` helpers used by the endpoint modules.

Endpoint modules are thin and purpose-specific: `auth.js`, `account.js`, `transfer.js`, `requests.js`.

### Pages

| Page | Highlights |
| --- | --- |
| **LoginPage** | Username/password form, error banner, spinner, link to register; redirects to the intended route on success. |
| **RegisterPage** | Full-name, username, email, optional phone, password; client-side validation mirrors backend rules; auto-login on success. |
| **DashboardPage** | Live balance card with skeleton loading, "Send money"/"Request money" actions, and the five most recent transactions; refreshes the cached balance. |
| **TransferPage** | Recipient type-ahead search (debounced 200 ms, stale-response-guarded via a sequence ref), amount + description, and an auto-rotated idempotency key; includes a "look up a transfer" panel by reference id. |
| **RequestsPage** | Tabbed Incoming / Outgoing / New-request views; Pay and Decline actions on pending incoming requests, with a receipt showing the settlement reference; refreshes balance after paying. |
| **HistoryPage** | Full transaction table (date, type, from, to, description, amount, status badge) with skeleton and empty states. |
| **NotFoundPage** | Simple 404 with a link home. |

### Styling & Theming

`src/index.css` is a raw-CSS design system:

- **Tokens** — brand gradient, surfaces, borders, text, semantic colors (danger/warning/success),
  radii, shadows, focus ring, and a max content width, all as CSS custom properties on `:root`.
- **Themes** — dark by default; `Layout`'s `useTheme` sets `data-theme="light|dark"` on the document
  element and persists the choice.
- **Components** — cards, buttons (`btn-primary/secondary/ghost/link`), fields, alerts, tabs, badges,
  tables, lists, suggestions, skeletons, empty states, and the app shell (top bar, nav, footer).
- **Responsiveness** — layout is built around `--maxw` with flexible grids and a `page-narrow`
  container for form-centric pages.

### Utilities

`src/utils/format.js`:

- `generateIdempotencyKey()` — uses `crypto.randomUUID()` when available, with a random fallback.
- `formatMoney(value, currency)` — `Intl.NumberFormat` currency formatting with graceful fallback.
- `formatDate(value)` — locale date/time string, `-` for invalid input.
- `statusBadgeClass(status)` — maps a status string to a badge CSS class (warning/success/danger).

---

## End-to-End Flows

**Register → funded account**

1. `POST /api/auth/register` creates the `User`, an `Account` with `app.initial-balance`, and an
   `INITIAL_FUNDING` transaction — atomically — and returns an `AuthResponse` with a JWT.
2. The SPA stores the token + profile, and `AuthProvider` marks the session authenticated.

**Send money**

1. On `/transfer`, the SPA searches users via `/api/account/users/search` (debounced) and lets the
   sender pick a recipient.
2. Submitting posts to `/api/transfers` with an auto-generated `idempotencyKey`.
3. `TransferService` locks both accounts (`FOR UPDATE`), checks funds, debits/credits, records a
   `TRANSFER` transaction, and evicts the balance cache.
4. The SPA shows a receipt with the reference id and refreshes the cached balance.

**Request → pay**

1. On `/requests`, a requester creates a request (`PENDING`).
2. The debtor sees it under Incoming and either **Pays** (`POST /api/requests/{id}/accept`) — settling
   as a `REQUEST_SETTLEMENT` transfer with pessimistic locks and balance checks — or **Declines**
   (`POST /api/requests/{id}/decline`).
3. Accepting returns the settling transaction so the UI can show a receipt; declining marks the
   request `DECLINED`.

---

## Production Hardening Notes

This is a **demo** built for a hackathon/carnival. Before treating it as production-ready:

- **Secrets & config** — always set `JWT_SECRET`; externalize DB/Redis credentials (env vars, secrets
  manager); never ship the placeholder password or secret.
- **Database** — use a migration tool (Flyway/Liquibase) instead of `ddl-auto=update`; pin Hibernate
  `dialect` explicitly.
- **CORS** — replace `allowedOriginPatterns("*")` with an explicit allow-list.
- **Transport** — terminate TLS in front of the API; consider `Secure`/`HttpOnly` cookies or shorter
  token lifetimes plus refresh tokens instead of a single long-lived bearer token.
- **Rate limiting & audit** — add request throttling and an audit trail for money movements.
- **Error surfaces** — the catch-all handler intentionally hides internals; keep it that way and add
  structured logging/tracing.
- **Cache resilience** — make Redis optional/graceful or use a local fallback cache when Redis is
  unavailable.
- **Frontend** — serve the built `dist/` over HTTPS behind a reverse proxy; keep the API base URL in
  environment configuration.

---

*MoneyMove — secure money movement demo · built with Spring Boot & React for PSTU IT Carnival 2026.*
