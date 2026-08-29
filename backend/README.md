# Money Movement Application — Backend

Production-grade backend for the **PSTU IT Carnival 2026** Money Movement hackathon.
A closed, simulated-funds ecosystem where users register with an auto-funded balance and
move/request money between one another.

- **Language/Framework:** Java 17, Spring Boot 3.4.5
- **Database:** PostgreSQL (JPA / Hibernate)
- **Cache:** Redis (Spring Cache)
- **Security:** Spring Security + JWT (stateless)
- **Docs:** Swagger UI at `/swagger-ui.html`
- **Health:** `/actuator/health`

---

## Features

| Capability | Endpoint | Notes |
| --- | --- | --- |
| Register | `POST /api/auth/register` | Auto-funds **BDT 100,000** via an `INITIAL_FUNDING` ledger entry |
| Login | `POST /api/auth/login` | Returns a JWT bearer token |
| Send money | `POST /api/transfers` | Atomic double-entry transfer |
| Transfer receipt | `GET /api/transfers/{referenceId}` | Lookup by reference |
| Request money | `POST /api/requests` | "Collect" flow — ask another user to pay you |
| Accept request | `POST /api/requests/{id}/accept` | Settles the request by moving money |
| Decline request | `POST /api/requests/{id}/decline` | Rejects the request |
| Incoming requests | `GET /api/requests/incoming` | Pending requests addressed to you |
| Outgoing requests | `GET /api/requests/outgoing` | Pending requests you created |
| My balance | `GET /api/account/me/balance` | |
| Balance by user | `GET /api/account/balance/{username}` | **Self-only** — returns `403 Forbidden` for any other username |
| Transaction history | `GET /api/account/me/history` | |
| User search | `GET /api/account/users/search?q=` | Min 2 chars, matches username/full name |

All `/api/**` endpoints except `/api/auth/**` require `Authorization: Bearer <token>`.

> **Authorization note:** Balances are private. `GET /api/account/balance/{username}` is
> restricted to the authenticated user's own username (enforced in `AccountController` via
> `ForbiddenException` → `403`). Use `GET /api/account/me/balance` for the caller's balance.

---

## Engineering decisions (why it is trustworthy)

Moving money looks simple (`A → ৳500 → B`) but real systems fail under concurrency,
retries, and bad input. The design defends against those:

1. **Double-entry ledger.** Every movement writes a single `Transaction` row and updates
   both accounts in one database transaction (`TransferService`). Money is never created
   or destroyed — it is only moved. This makes every balance auditable.

2. **Concurrency safety.** Transfers run under `SERIALIZABLE` isolation and take
   `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`) locks on **both** accounts before
   mutating balances (`AccountRepository.findByUserIdForUpdate`). A `@Version` optimistic
   lock on `Account` is a second line of defense. Result: no lost updates, no
   overdrawing, even with many simultaneous transfers.

3. **Idempotency.** Clients may send an `idempotencyKey` on a transfer. A retried request
   with the same key returns the original transaction instead of moving money twice —
   safe against network retries and double-clicks.

4. **Money correctness.** Balances and amounts use `BigDecimal` (never `float`/`double`),
   scaled to 2 decimals. Self-transfers, zero/negative amounts, and insufficient balances
   are rejected with clear errors.

5. **Stateless auth.** JWT bearer tokens; no server-side session. Passwords hashed with
   BCrypt.

6. **Caching.** Balances are cached in Redis (TTL 60s) to absorb read load at scale, with
   the source of truth always in PostgreSQL.

---

## Error handling & status codes

All errors are returned as a consistent JSON envelope (`ApiError`):

```json
{ "status": 404, "error": "Not Found", "message": "User not found", "timestamp": "2026-08-29T12:00:00Z" }
```

| HTTP | Trigger | Exception |
| --- | --- | --- |
| `400` | Validation failure / bad input / illegal argument | `MethodArgumentNotValidException`, `IllegalArgumentException`, `BadRequestException` |
| `401` | Invalid credentials / missing token | `BadCredentialsException` |
| `403` | Accessing another user's balance | `ForbiddenException` |
| `404` | Unknown user / account / request / transaction | `ResourceNotFoundException` |
| `409` | Duplicate username/email on register | `ConflictException` |
| `409` | Concurrent update conflict (optimistic lock) | `OptimisticLockException` / `PessimisticLockException` |
| `500` | Unexpected server error | generic `Exception` |

Validation errors flatten Spring's field errors into a single readable `message`
(e.g. `"amount: must be greater than 0"`).

---

## Project structure

```
src/main/java/com/pstu/backend/
├── domain/          # User, Account, Transaction, MoneyRequest (+ enums)
├── repository/      # Spring Data JPA repositories (pessimistic-lock queries)
├── service/         # AuthService, TransferService, MoneyRequestService, AccountService
├── controller/      # REST controllers
├── dto/             # request/response records
├── security/        # JWT util, filter, SecurityConfig, UserDetailsService
├── exception/       # domain exceptions (ResourceNotFound, Conflict, Forbidden, InsufficientBalance, BadRequest) + GlobalExceptionHandler
└── config/          # caching config
```

---

## Running locally

### Prerequisites
- Java 17
- PostgreSQL (database `moneyapp`, user `postgres`, password `mysecretpassword`)
- Redis

### Start infrastructure (example)
```bash
# PostgreSQL + Redis via Docker
docker run -d --name moneyapp-postgres -e POSTGRES_DB=moneyapp \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=mysecretpassword \
  -p 5432:5432 postgres:16-alpine
docker run -d --name moneyapp-redis -p 6379:6379 redis:7-alpine
```

### Run the app
```bash
./mvnw spring-boot:run        # or: mvn spring-boot:run
# or build & run the jar
mvn clean package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

The app starts on `http://localhost:8080`.

### Configuration
Set via `application.properties` or environment variables:

| Property | Default | Description |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/moneyapp` | DB URL |
| `spring.datasource.username` | `postgres` | DB user |
| `spring.datasource.password` | `mysecretpassword` | DB password |
| `spring.data.redis.host` | `localhost` | Redis host |
| `app.initial-balance` | `100000` | Auto-funding amount (BDT) |
| `app.currency` | `BDT` | Account currency |
| `jwt.secret` | (dev placeholder) | **Set a strong secret in production** |
| `jwt.expiration-ms` | `86400000` | Token lifetime (24h) |

> ⚠️ Never use the default `jwt.secret` in production. Provide `JWT_SECRET` (≥256-bit).

---

## Docker

The provided `Dockerfile` is a **single-stage** image based on `eclipse-temurin:17-jre-alpine`.
It expects a pre-built Spring Boot JAR to already exist at `target/*.jar` (produced by
`./mvnw package`); it does **not** build the app inside Docker. The JAR is copied in as
`app.jar` and run via `java -jar app.jar` on port `8080`.

### 1. Build the JAR first
```bash
./mvnw clean package -DskipTests
# produces target/backend-0.0.1-SNAPSHOT.jar
```

### 2. Build the image
```bash
docker build -t money-backend .
```

### 3. Run the container
```bash
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/moneyapp \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=mysecretpassword \
  -e SPRING_DATA_REDIS_HOST=redis \
  -e JWT_SECRET=your-strong-secret \
  money-backend
```

> The container only runs the JRE — it has no Maven, so the JAR **must** be built on the
> host (step 1) before `docker build`. Mount or network-connect Postgres and Redis to the
> container; the defaults point at `localhost`, which is the host inside Docker — use the
> service names (e.g. `postgres`, `redis`) or `host.docker.internal` instead.

---

## Kubernetes (MicroK8s)

`k8s/backend.yaml` deploys a 3-replica `money-backend` Deployment behind a
`LoadBalancer` Service (port 80 → 8080) with readiness/liveness probes on
`/actuator/health`. Postgres and Redis are assumed to already exist in the cluster.

```bash
microk8s kubectl apply -f k8s/backend.yaml
microk8s kubectl get svc money-backend   # EXTERNAL-IP assigned by MetalLB
```

---

## API examples

```bash
# Register
curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"secret123","fullName":"Alice Khatun","phoneNumber":"01700000000"}'

# Login -> copy the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"secret123"}' | jq -r .token)

# Send ৳2,500 to bob (idempotency key protects against retries)
curl -s -X POST http://localhost:8080/api/transfers \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"toUsername":"bob","amount":2500.00,"description":"lunch","idempotencyKey":"req-001"}'

# Request ৳1,200 from bob
curl -s -X POST http://localhost:8080/api/requests \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"fromUsername":"bob","amount":1200.00,"note":"concert ticket"}'

# My balance
curl -s http://localhost:8080/api/account/me/balance -H "Authorization: Bearer $TOKEN"
```

---

## Testing

```bash
mvn test
```

**36 tests, all passing**, covering:

- `AuthServiceTest` — registration (auto-funding, duplicate rejection), login paths.
- `TransferServiceTest` — money movement, self-transfer/unknown-recipient/insufficient-balance
  rejection, amount rounding, idempotency.
- `TransferServiceConcurrencyTest` — 20-thread concurrent transfers prove money
  conservation with no overdraw; idempotency prevents duplicate posting.
- `MoneyRequestServiceTest` — create/accept/decline flows and all rejection cases.
- `AccountServiceTest` — balance, history, and user search.
- `JwtUtilTest` — token generation, validation, expiry, and tamper rejection.
- `BackendApplicationTests` — full Spring context loads.

Unit tests use Mockito; the concurrency test runs against an in-memory H2 database with
caching disabled, so no external services are required to run the suite.

---

## Notes / scope

This is a **closed money ecosystem with simulated funds** — no real banks, cards, or
payment gateways. The focus is a small but thoughtful, correct product: correct money
movement under concurrency, safe retries, and clear, auditable records.
