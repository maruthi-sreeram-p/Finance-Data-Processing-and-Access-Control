# FinTrack

**Finance Data Processing & Access Control Platform**

A backend-focused personal finance platform built around one question that most CRUD
tutorials skip: *given that you know who is calling, how do you prove they are allowed
to touch this particular financial record?*

---

## Contents

- [Problem statement](#problem-statement)
- [Features](#features)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Database design](#database-design)
- [Authentication](#authentication)
- [Authorization](#authorization)
- [API reference](#api-reference)
- [Error handling](#error-handling)
- [Setup](#setup)
- [Environment variables](#environment-variables)
- [Running locally](#running-locally)
- [Running with Docker](#running-with-docker)
- [Testing](#testing)
- [API documentation](#api-documentation)
- [Design decisions](#design-decisions)
- [Future improvements](#future-improvements)

---

## Problem statement

Personal finance data is among the most sensitive a user will hand to an application.
Yet the failure mode that leaks it is rarely exotic — it is an endpoint that checks
*whether* you are logged in and forgets to check *whose* record you asked for.

```
GET /api/transactions/10    →  200 OK, your rent payment
GET /api/transactions/11    →  200 OK, somebody else's rent payment
```

That is a **horizontal privilege escalation** (OWASP calls it Broken Object Level
Authorization), and it is the single most common serious flaw in REST APIs.

FinTrack treats it as the central design problem rather than an afterthought.
Authorization is enforced in the service layer, where the identity of the caller and
the identity of the record are both in scope — the only place the question can
actually be answered. A request for a record that exists but belongs to someone else
returns the same `404` as a record that does not exist at all, so the API cannot be
walked to discover which ids are real.

---

## Features

| Area | What is implemented |
|---|---|
| **Authentication** | Registration, login, stateless JWT bearer tokens, BCrypt password hashing (strength 12) |
| **Authorization** | Three roles, URL-level rules, method-level `@PreAuthorize`, and service-level resource ownership |
| **Transactions** | Full CRUD, ownership-scoped, with `BigDecimal` money handling |
| **Filtering** | By type, category (id or name), date range and amount range, composed as JPA Specifications |
| **Pagination & sorting** | Page/size, whitelisted sort properties, guaranteed-stable ordering |
| **Categories** | Database-driven reference data, readable by all, managed by administrators |
| **Dashboard** | Totals, balance, per-category breakdown, monthly trend and recent activity — all computed by database aggregate queries |
| **Validation** | Jakarta Bean Validation on every request DTO, with all field errors reported at once |
| **Error handling** | One `ApiError` shape for every failure, from the filter chain through to Spring MVC |
| **Documentation** | OpenAPI 3 with a working "Authorize" button in Swagger UI |
| **Migrations** | Flyway-managed schema, `ddl-auto=validate` |
| **Testing** | 209 backend tests: unit, repository, RBAC matrix, security and end-to-end |
| **Deployment** | Multi-stage Dockerfile, Docker Compose stack, health endpoint |

---

## Tech stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.16 |
| Web | Spring Web (MVC) |
| Persistence | Spring Data JPA, Hibernate 6.6 |
| Database | MySQL 8 (H2 in MySQL mode for tests) |
| Migrations | Flyway |
| Security | Spring Security 6, jjwt 0.12.7 |
| Validation | Jakarta Bean Validation (Hibernate Validator) |
| Documentation | springdoc-openapi 2.9.0 |
| Testing | JUnit 5, Mockito, AssertJ, Spring Security Test, MockMvc |
| Build | Maven (wrapper committed) |
| Container | Docker, Docker Compose |

---

## Architecture

A **modular monolith** in clean layers. No microservices, no message broker, no cache —
none of them would solve a problem this application actually has, and each would add
operational surface for nothing.

```
      HTTP request
           │
           ▼
 ┌──────────────────────┐
 │  Security filters    │  JwtAuthenticationFilter → SecurityContext
 │                      │  URL rules; 401/403 rendered as JSON
 └──────────┬───────────┘
            ▼
 ┌──────────────────────┐
 │  Controller          │  Binding, @Valid, status codes, Location headers
 │                      │  @PreAuthorize role gates. No business logic.
 └──────────┬───────────┘
            ▼
 ┌──────────────────────┐
 │  Service             │  Business rules, transaction boundaries
 │                      │  ►► OWNERSHIP CHECKS LIVE HERE ◄◄
 └──────────┬───────────┘
            ▼
 ┌──────────────────────┐
 │  Repository          │  Spring Data JPA, Specifications, aggregate queries
 └──────────┬───────────┘
            ▼
        MySQL
```

Entities never leave the service layer. Every response is a DTO, mapped explicitly, so
a new column on an entity cannot silently appear in an API response — someone has to
add it to a mapper first.

### Why authorization is layered

| Layer | Answers | Why it cannot be the only one |
|---|---|---|
| `SecurityConfig` URL rules | "Is this path public? Does it need a role?" | A URL pattern cannot express *"this transaction belongs to this caller"* |
| `@PreAuthorize` on controllers | "Does the caller hold the required role?" | Roles are coarse; every USER has the same one |
| **Service layer** | **"Does this specific record belong to this caller?"** | This is the only layer where both identities are in scope |

---

## Project structure

```
finTrack/
├── backend/            Spring Boot API  (this README's main subject)
├── frontend/           React client     (see frontend/README.md)
└── docker-compose.yml  MySQL + API
```

### Backend

```
backend/src/main/java/com/fintrack
├── config          SecurityConfig, OpenApiConfig, JwtProperties, PasswordEncoderConfig
├── controller      Auth, User, Transaction, Category, Dashboard
├── dto
│   ├── auth        RegisterRequest, LoginRequest, AuthResponse
│   ├── user        UserResponse, UpdateUserRequest, AdminUpdateUserRequest, ChangePasswordRequest
│   ├── transaction TransactionRequest, TransactionResponse, TransactionFilter
│   ├── category    CategoryRequest, CategoryResponse
│   ├── dashboard   DashboardResponse, CategoryBreakdown, MonthlySummary
│   └── common      ApiError, PagedResponse
├── entity          User, Transaction, Category, AuditableEntity + enums
├── repository      Repositories, TransactionSpecifications, projection/
├── service         Interfaces
│   └── impl        Implementations
├── security        JwtService, JwtAuthenticationFilter, CustomUserDetailsService,
│                   AuthenticatedUser, CurrentUserProvider, LedgerAccessPolicy,
│                   RestAuthenticationEntryPoint, RestAccessDeniedHandler
├── exception       ErrorCode, FinTrackException + subtypes, GlobalExceptionHandler
├── mapper          UserMapper, TransactionMapper, CategoryMapper
└── util            EmailNormalizer, SortSanitizer
```

---

## Database design

```
┌──────────────────┐          ┌────────────────────────┐         ┌──────────────────┐
│      users       │          │      transactions      │         │    categories    │
├──────────────────┤          ├────────────────────────┤         ├──────────────────┤
│ id           PK  │─────┐    │ id                 PK  │    ┌────│ id           PK  │
│ name             │     │    │ user_id            FK  │────┘    │ name         UQ  │
│ email        UQ  │     └───►│ category_id        FK  │◄────────│ description      │
│ password_hash    │      1:N │ type                   │     N:1 │ type             │
│ role             │          │ amount   DECIMAL(19,2) │         │ created_at       │
│ status           │          │ description            │         │ updated_at       │
│ created_at       │          │ transaction_date       │         └──────────────────┘
│ updated_at       │          │ created_at             │
└──────────────────┘          │ updated_at             │
                              └────────────────────────┘
```

**Relationships are deliberately unidirectional.** `Transaction` has a `@ManyToOne` to
`User`; `User` has *no* `@OneToMany` back. A user can accumulate tens of thousands of
transactions, and mapping the collection would invite `user.getTransactions()` calls
that load an entire ledger into memory and bypass pagination. The ledger is only ever
read through a paginated repository query.

Both associations on `Transaction` are `LAZY`. Listing 20 transactions with eager
loading would issue 40 extra queries and pull every owner's password hash into memory;
the queries that need the category use an explicit entity graph instead.

**Indexes**

| Index | Serves |
|---|---|
| `uk_users_email` | Login lookup, and the real guarantee of email uniqueness under concurrency |
| `idx_transactions_user_date` | The default listing, the date-range filter, and every dashboard aggregate |
| `idx_transactions_user_type` | The `?type=` filter |
| `idx_transactions_category` | The category filter and the category-deletion guard |

**Constraints as a last line of defence.** `CHECK` constraints mirror the enums and
enforce `amount > 0` at the database level, so a write that bypasses the API layer
still cannot corrupt the ledger.

**Money is `DECIMAL(19,2)` and `BigDecimal` in Java — never `double`.** Binary floating
point cannot represent `0.10` exactly; summing a few thousand expenses would drift
visibly from the true total.

**Migrations, not `ddl-auto=update`.** Flyway owns the schema; Hibernate runs with
`ddl-auto=validate` and fails the build if an entity and its migration ever disagree.

| Migration | Contents |
|---|---|
| `V1__create_core_schema.sql` | `users`, `categories`, `transactions`, constraints, indexes |
| `V2__seed_categories.sql` | The 12 reference categories |

The SQL is restricted to the subset that behaves identically on MySQL 8 and H2 in MySQL
mode, so the test suite exercises the real migrations rather than a Hibernate-generated
schema.

---

## Authentication

```
POST /api/auth/register  ─┐
POST /api/auth/login     ─┴─►  { accessToken, tokenType, expiresIn, user }

Subsequent requests:  Authorization: Bearer <accessToken>
```

**Passwords** are hashed with BCrypt at strength 12 (the Spring default is 10). Each
increment doubles the work an offline attacker must repeat per guess; 12 keeps a single
hash in the low hundreds of milliseconds — imperceptible at login, expensive at scale
against a stolen dump.

**The token subject is the numeric user id, not the email.** An email is mutable:
keying on it would either invalidate live tokens whenever someone changed their address
or, worse, resolve to a different account if an address were ever reused.

**The account is re-read from the database on every request** rather than reconstructed
from the token's claims. That one indexed lookup is what makes a suspension or a role
change take effect immediately instead of whenever the token happens to expire. The
`email` and `role` claims ride along for client convenience, but nothing on the server
trusts them.

**Every login failure returns one identical 401.** Unknown email, wrong password,
suspended account, deactivated account — all the same message. Any difference would turn
the login endpoint into an oracle for which addresses are registered.

**Registration has no role field at all.** Not ignored — absent from the DTO. Accepting
one on a public endpoint would make "administrator" self-service.

---

## Authorization

### Roles

| Role | Own data | Read others' ledgers | Org-wide totals | Write others' records | Account admin | Category admin |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| `USER` | full | ✗ | ✗ | ✗ | ✗ | ✗ |
| `MANAGER` | full | ✓ | ✓ | ✗ | ✗ | ✗ |
| `ADMIN` | full | ✓ | ✓ | ✗ | ✓ | ✓ |

Enforced in three places, each answering the question only it can:

| Check | Where | Why there |
|---|---|---|
| Is this path public, and does it need a role? | `SecurityConfig` URL rules | Cheap, and applied before any application code runs |
| Does the caller hold the required role? | `@PreAuthorize` on controllers | A pure role question with no resource to inspect |
| Does *this record* belong to the caller? | Service layer | The only layer where the caller and the record are both in scope |

### A note on MANAGER

The domain model has no team, department or business-unit concept, so MANAGER is
**organization-wide read-only**, not team-scoped. Inventing a team hierarchy to
make the role look richer would have meant fabricating a data model that nothing
else uses. A manager can read any individual ledger and the organization-wide
aggregate; they cannot write anything they do not own, and cannot administer
accounts at all. If teams are added later, `LedgerAccessPolicy` is the single
place that would need to learn about them.

### The rule that matters

**Read access widens with privilege. Write access never does.**

A manager needs cross-user visibility to produce reports; an administrator needs it to
support users. But *nobody* — administrators included — may create, alter or delete
another person's financial records. A ledger a third party can silently rewrite is not
trustworthy, and no support task requires it: the legitimate remedies are for the owner
to correct the entry, or for an administrator to suspend the account.

This is enforced structurally, not by convention. `LedgerAccessPolicy.canModify()` takes
no role argument, so there is nowhere to add an "administrators are exempt" clause later.

### How ownership is enforced

Ownership is folded into the `WHERE` clause rather than checked after loading:

```java
Optional<Transaction> findByIdAndUserId(Long id, Long userId);
```

A record belonging to someone else comes back as an empty `Optional`, indistinguishable
from one that does not exist, and becomes the same `404`.

**Why 404 and not 403?** A `403` would confirm the id is real. An attacker could then
walk the id space and learn exactly how many transactions the system holds and which
ids are live. Returning `404` leaks nothing.

### Escalation paths that are closed

| Attempt | Outcome |
|---|---|
| `POST /api/auth/register` with `"role": "ADMIN"` | Discarded — the DTO has no such component. Account is `USER`. |
| `PUT /api/users/me` with `"role": "ADMIN"` | Discarded — `UpdateUserRequest` deliberately omits role and status. |
| `POST /api/transactions` with `"userId": <someone else>` | Discarded — the owner always comes from the security context. |
| `PUT /api/transactions/{id}` with a different `userId` | The mapper never writes the owner field; the record cannot change hands. |
| `GET /api/transactions/{someone else's id}` | `404`, byte-identical to a missing record. |
| `GET /api/transactions?userId=<someone else>` as `USER` | Silently pinned to the caller's own ledger. |
| `PUT`/`DELETE /api/transactions/{id}` as `ADMIN` on another user's record | `404` — write access does not widen. |
| `?sort=user.passwordHash` | Dropped by the sort whitelist. |
| `GET /api/dashboard/system` as `USER` or `MANAGER`-less | **403** — system-wide financial data. |
| Using a token minted before a demotion | Authorities come from the database, not the token claim; the demotion applies at once. |
| Demoting or suspending the last active `ADMIN` | `403` — the system cannot be locked out of its own administration. |

---

## API reference

### Public

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account, receive a token · `201` |
| `POST` | `/api/auth/login` | Exchange credentials for a token · `200` |
| `GET` | `/actuator/health` | Liveness probe |
| `GET` | `/swagger-ui.html` | Interactive documentation |

### Authenticated — own profile

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/users/me` | Own profile |
| `PUT` | `/api/users/me` | Update own name and/or email |
| `PUT` | `/api/users/me/password` | Change password (re-verifies the current one) · `204` |
| `DELETE` | `/api/users/me` | Deactivate own account (soft delete) · `204` |

### Authenticated — transactions

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/transactions` | Create · `201` with `Location` |
| `GET` | `/api/transactions` | List, filtered and paginated |
| `GET` | `/api/transactions/{id}` | Read one |
| `PUT` | `/api/transactions/{id}` | Replace (owner only) |
| `DELETE` | `/api/transactions/{id}` | Delete (owner only) · `204` |

**Query parameters for the listing**

| Parameter | Type | Meaning |
|---|---|---|
| `type` | `INCOME` \| `EXPENSE` | Direction |
| `category` | string | Category name, case-insensitive |
| `categoryId` | long | Category id (wins over `category`) |
| `from`, `to` | ISO date | Inclusive date range |
| `minAmount`, `maxAmount` | decimal | Inclusive amount range |
| `userId` | long | Another user's ledger — `ADMIN`/`MANAGER` only |
| `page`, `size` | int | Pagination (default `0`, `20`) |
| `sort` | `property,dir` | `transactionDate`, `amount`, `type`, `createdAt`, `updatedAt`, `id` |

```
GET /api/transactions?type=EXPENSE&category=Food&from=2026-08-01&to=2026-08-31&page=0&size=20&sort=amount,desc
```

### Authenticated — dashboard and categories

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/dashboard` | Totals, breakdown, monthly trend, recent activity. Accepts `from`, `to`, `userId` |
| `GET` | `/api/dashboard/system` | **ADMIN or MANAGER.** Organization-wide totals across every account. Accepts `from`, `to`. No per-user breakdown |
| `GET` | `/api/categories` | All categories, or those usable by `?type=` |
| `GET` | `/api/categories/{id}` | One category |

### Administrator only

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/users` | List accounts, filterable by `role` and `status` |
| `GET` | `/api/users/{id}` | Read an account |
| `PUT` | `/api/users/{id}` | Update an account — the only route to role and status |
| `DELETE` | `/api/users/{id}` | Deactivate an account · `204` |
| `POST` | `/api/categories` | Create a category · `201` |
| `PUT` | `/api/categories/{id}` | Update a category |
| `DELETE` | `/api/categories/{id}` | Delete a category · `204` |

### Status codes

| Code | When |
|---|---|
| `200` | Successful read or update |
| `201` | Resource created (with `Location`) |
| `204` | Successful action with nothing to return |
| `400` | Validation failure, malformed request, or a broken domain rule |
| `401` | No credentials, or invalid/expired ones |
| `403` | Authenticated but not permitted |
| `404` | Not found — or not the caller's to see |
| `409` | Conflict with existing data |
| `500` | Unexpected server error (logged in full, never disclosed) |

---

## Error handling

Every failure returns the same shape, whether it came from the security filter chain,
Bean Validation, a domain rule, or Spring MVC's own dispatch:

```json
{
  "timestamp": "2026-08-24T09:15:30.123Z",
  "status": 404,
  "error": "RESOURCE_NOT_FOUND",
  "message": "Transaction not found with id: 10",
  "path": "/api/transactions/10"
}
```

Validation failures add `fieldErrors`, reporting every rejected field at once so a form
can show all its errors after a single submission:

```json
{
  "timestamp": "2026-08-24T09:15:30.123Z",
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/transactions",
  "fieldErrors": [
    { "field": "amount", "message": "Amount must be at least 0.01" },
    { "field": "transactionDate", "message": "Transaction date cannot be in the future" }
  ]
}
```

Clients should branch on `error`, which is stable; `message` is for humans and may change.

**No response ever contains a stack trace, an exception class name, a SQL fragment or a
constraint name.** That is asserted by a test, not just intended.

| `error` | Status |
|---|---|
| `VALIDATION_FAILED` | 400 |
| `INVALID_TRANSACTION` | 400 |
| `BAD_REQUEST` | 400 |
| `UNAUTHORIZED` | 401 |
| `FORBIDDEN` | 403 |
| `RESOURCE_NOT_FOUND` | 404 |
| `DUPLICATE_RESOURCE` | 409 |
| `INTERNAL_ERROR` | 500 |

---

## Setup

### Prerequisites

- **JDK 21 or newer** (the project targets 21; it builds and runs on 22+)
- **MySQL 8** — or Docker, which supplies its own
- Maven is **not** required; the wrapper is committed

### 1. Clone

```bash
git clone <repository-url> && cd finTrack
```

All backend commands below run from the `backend/` directory.

### 2. Create the database and an application user

Run this once as a MySQL administrator. Choose your own password.

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS fintrack CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'fintrack'@'localhost' IDENTIFIED BY 'your-password'; GRANT ALL PRIVILEGES ON fintrack.* TO 'fintrack'@'localhost'; FLUSH PRIVILEGES;"
```

Flyway creates the tables on first start — do not create them by hand.

### 3. Configure the environment

```bash
cp .env.example .env
```

Then edit `.env`. Generate a real signing key rather than inventing one:

```bash
openssl rand -base64 32
```

`.env` is git-ignored. **Never commit it.**

---

## Environment variables

| Variable | Required | Default | Purpose |
|---|:---:|---|---|
| `DB_URL` | ✓ | — | JDBC URL |
| `DB_USERNAME` | ✓ | — | Database user |
| `DB_PASSWORD` | ✓ | — | Database password |
| `JWT_SECRET` | ✓ | — | Base64 signing key, ≥ 32 bytes decoded |
| `JWT_EXPIRATION_MS` | | `3600000` | Token lifetime (1 hour) |
| `SERVER_PORT` | | `8080` | HTTP port |
| `LOG_LEVEL_APP` | | `INFO` | Log level for `com.fintrack` |
| `MYSQL_DATABASE` | Docker | `fintrack` | Database name |
| `MYSQL_ROOT_PASSWORD` | Docker | — | MySQL root password |
| `MYSQL_HOST_PORT` | Docker | `3307` | Published MySQL port |
| `APP_HOST_PORT` | Docker | `8080` | Published application port |

The application **refuses to start** if `JWT_SECRET` is missing, is not Base64, or
decodes to fewer than 32 bytes — a misconfigured deployment fails immediately and
loudly rather than at the first login attempt in production.

---

## Running locally

```bash
cd backend && ./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd`. The API is then at `http://localhost:8080`.

### Without MySQL

For frontend work, or to try the API before provisioning a database, the `local`
profile runs the same application against a file-backed H2:

```bash
cd backend && ./mvnw spring-boot:run -Plocal -Dspring-boot.run.profiles=local
```

This is the real backend — same security chain, same Flyway migrations, same
authorization rules — with a different datasource. H2 is confined to the `local`
Maven profile so it never reaches the deployable jar or the Docker image.

Under this profile only, the account named by `fintrack.local.admin-email`
(default `admin@fintrack.test`) is promoted to ADMIN on startup if it exists.
Registration always produces a USER and only an administrator can promote, so a
fresh database otherwise has no way to grow its first administrator. Register the
account, restart, and it becomes an admin.

### Running the frontend

```bash
cd frontend && npm install && npm run dev
```

The client is then at `http://localhost:5173`, which is the origin the backend
allows through CORS by default. See [frontend/README.md](frontend/README.md).

### A five-request tour

```bash
# 1. Register — returns a token
curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Abhiram P","email":"abhiram@example.com","password":"Passw0rd123"}'
```

```bash
# 2. Log in and keep the token in a shell variable
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"abhiram@example.com","password":"Passw0rd123"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
```

```bash
# 3. List the categories so you can pick a real id
curl -s http://localhost:8080/api/categories -H "Authorization: Bearer $TOKEN"
```

```bash
# 4. Record an expense (category 3 is Rent in the seeded data)
curl -s -X POST http://localhost:8080/api/transactions \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"type":"EXPENSE","amount":1250.00,"categoryId":3,"description":"August rent","transactionDate":"2026-08-01"}'
```

```bash
# 5. Read the dashboard
curl -s http://localhost:8080/api/dashboard -H "Authorization: Bearer $TOKEN"
```

### Creating the first administrator

Registration always produces a `USER`, by design. Promote the first account directly in
the database; from then on, administrators promote others through `PUT /api/users/{id}`.

```bash
mysql -u fintrack -p fintrack -e "UPDATE users SET role = 'ADMIN' WHERE email = 'abhiram@example.com';"
```

---

## Running with Docker

Brings up MySQL and the application together. Requires only Docker.

```bash
cp .env.example .env
```

```bash
docker compose up --build
```

Compose waits for MySQL's health check before starting the application, so Flyway never
attempts to migrate during the window when MySQL restarts itself mid-initialization.
MySQL is published on **3307** by default so it does not collide with a server already
running on the host.

```bash
docker compose down
```

Add `-v` to that command to discard the database volume as well.

---

## Testing

```bash
cd backend && ./mvnw test
```

**209 tests**, all green, requiring no external infrastructure — the suite runs against
in-memory H2 in MySQL-compatibility mode and executes the real Flyway migrations, so a
mapping that drifts from the schema fails the build.

| Suite | Tests | Covers |
|---|---:|---|
| `AuthControllerIntegrationTest` | 20 | Registration, login, bearer tokens, escalation attempts |
| `TransactionControllerIntegrationTest` | 31 | CRUD, ownership, filtering, pagination, sorting |
| `DashboardControllerIntegrationTest` | 15 | Aggregates against hand-checked totals |
| `RbacMatrixTest` | 30 | The whole role matrix in one auditable place: authority wiring, ownership, and every role against every class of operation |
| `UserEndpointAuthorizationTest` | 13 | RBAC on every account endpoint |
| `TransactionServiceImplTest` | 13 | Which repository method each operation uses |
| `JwtServiceTest` | 12 | Tampering, expiry, foreign keys, weak secrets |
| `ErrorContractIntegrationTest` | 9 | One error shape; no internals leaked |
| `SortSanitizerTest` | 9 | Whitelisting and stable pagination |
| `UserServiceImplTest` | 15 | Profile, password, last-administrator guards |
| `CategoryServiceImplTest` | 8 | Naming, deletion guards |
| `LedgerAccessPolicyTest` | 8 | The access rules, exhaustively |
| `OpenApiDocumentationTest` | 7 | The generated document |
| Repository tests | 15 | Schema agreement, constraints, monetary scale |
| `HealthEndpointTest` | 3 | Probe reachable, internals not exposed |
| `FinTrackApplicationTests` | 1 | Context loads; migrations and mappings agree |

Some choices worth noting:

- Security tests use **real tokens through the real filter chain**, not
  `@WithMockUser`. The annotation injects an authentication directly into the context
  and would keep passing even if the JWT filter were broken or removed entirely.
- Ownership tests always have **two users owning data**, so every request has a wrong
  answer available to it. A suite that only ever exercises one user's records cannot
  detect a missing ownership check.
- Dashboard tests assert against **totals worked out by hand**, with a second user's
  transactions present throughout — an aggregate missing its `WHERE user_id` clause
  produces visibly wrong numbers rather than passing quietly.

Run one suite:

```bash
cd backend && ./mvnw test -Dtest=TransactionControllerIntegrationTest
```

### Frontend tests

```bash
cd frontend && npm test
```

**53 tests** covering route guarding, form validation, the API error layer, table
rendering and the date-parsing trap described in
[frontend/README.md](frontend/README.md).

---

## API documentation

With the application running:

| | |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

To call protected endpoints from the page: register or log in, copy the `accessToken`,
press **Authorize**, and paste the raw token — Swagger adds the `Bearer ` prefix itself.

---

## Design decisions

Choices that were not obvious, and the reasoning behind them.

**Flyway from the start, not `ddl-auto=update`.** Retrofitting migrations onto a
Hibernate-generated schema is painful; doing it on day one costs nothing. `validate`
then fails the build the moment an entity and its migration disagree.

**Enum column for `role`, not normalized RBAC tables.** FinTrack has three fixed roles
and no runtime role management. Four extra tables and their joins would buy nothing
today. Authorization is expressed against `Role.authority()` rather than against the
constants directly, so a lookup table can replace the enum without touching a controller.

**Category type is `INCOME`/`EXPENSE`/`BOTH`.** This gives `InvalidTransactionException`
real work: filing a salary under "Rent" is rejected. Without it, every dashboard
aggregate built on category would quietly rot. `BOTH` exists for genuinely
bidirectional categories like Investment.

**H2 for tests rather than Testcontainers.** The suite must run with no external
infrastructure. Testcontainers would be more faithful, but adds tens of seconds per run
and image-pull flakiness. The migrations are kept to SQL that behaves identically on
both engines, so H2 still exercises the real schema. Testcontainers is a documented
future upgrade, not an oversight.

**`PagedResponse` instead of returning Spring's `Page`.** `Page`'s JSON shape is an
implementation detail that has changed between Spring Data versions, and it serializes
the whole `Pageable` including sort internals. Spring Boot 3.3+ warns about exactly this.

**Every sort gets an `id` tiebreaker.** Without a unique final key, rows sharing a sort
value have no defined order between queries — so a paginated scan can return one row on
two pages and omit another entirely. This is a real bug, easily missed.

**Deletion is soft.** Removing a user row would cascade away their entire financial
history and break the foreign keys referencing it. Accounts are marked `INACTIVE`.

**`PasswordEncoder` lives outside `SecurityConfig`.** The filter chain depends on an
authentication provider, which depends on the encoder, while services need the encoder
too. Declaring it alongside the filter chain is the usual way a circular bean dependency
gets introduced into a Spring Security setup.

**No Redis, Kafka, or microservices.** None of them solves a problem this application
has. Each would add operational surface, a failure mode, and a thing to explain.

---

## Future improvements

Not implemented. Listed as the natural next steps, with the design already shaped to
accommodate them.

- **Refresh tokens** with rotation and a revocation list, so access tokens can be
  short-lived without forcing frequent logins
- **Budgets and financial goals** — per-category monthly limits with progress tracking
- **Recurring transactions**, which is also what would justify relaxing the
  no-future-dates rule
- **CSV import/export** and PDF statements
- **Testcontainers** alongside H2, so CI verifies against real MySQL
- **Rate limiting** on the authentication endpoints
- **Audit log** of administrative actions
- **Redis caching** for the dashboard, once measurement shows it is needed
- **React frontend**
- **CI/CD pipeline** running the suite on every push

---

## Licence

MIT
