# Mini Transaction Ledger

A double-entry transaction ledger. Accounts are opened, money is deposited, withdrawn and
transferred, and every movement is recorded as an immutable ledger entry that carries the balance
it left behind.

Submitted for the MISL Fresher Assessment Project (Problem 1 — Mini Transaction Ledger).

The whole system runs with one command:

```bash
docker compose up --build
```

Then open **http://localhost:4200**.

---

## Contents

- [Tech stack](#tech-stack)
- [Running it](#running-it)
- [What the app does](#what-the-app-does)
- [API](#api)
- [Architecture](#architecture)
- [How a request flows](#how-a-request-flows)
- [Inner workings](#inner-workings)
- [Tests](#tests)
- [Docker setup](#docker-setup)
- [Project layout](#project-layout)

---

## Tech stack

| Layer | Choice | Version |
|---|---|---|
| Backend | Java, Spring Boot (Web, Data JPA, Validation) | 21, 3.5.16 |
| Database | PostgreSQL | 16 |
| Migrations | Flyway | bundled with Boot |
| Frontend | Angular, TypeScript | 22.1, 6.0 |
| Web server | nginx (serves the build, proxies `/api`) | 1.27 |
| Containers | Docker multi-stage builds, Docker Compose | — |

No UI component library, no state-management library, no CSS framework. The frontend is plain
Angular with hand-written CSS, and every dependency in `package.json` ships with the framework.

---

## Running it

### With Docker (the intended way)

```bash
docker compose up --build
```

| Service | URL | Notes |
|---|---|---|
| Frontend | http://localhost:4200 | nginx; also proxies `/api` to the backend |
| Backend | http://localhost:8080/api/health | reachable directly for testing |
| Database | `localhost:5433` | mapped off 5432 to avoid clashing with a local PostgreSQL |

`--build` matters: `docker compose up` on its own reuses images that already exist and will not
pick up source changes.

To stop, and to discard the database volume:

```bash
docker compose down     # stop, keep the data
docker compose down -v  # stop and delete the data
```

Credentials default to `ledger` / `ledger` for convenience, so the project starts with no setup.
They are read from the environment (`POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`) and a
real deployment overrides them with a `.env` file. Nothing is hard-coded in the source.

### Without Docker

PostgreSQL must be listening on `localhost:5433` with database, user and password all `ledger`.

```bash
cd backend  && ./mvnw spring-boot:run    # http://localhost:8080
cd frontend && npm install && npm start  # http://localhost:4200
```

In development the Angular dev server proxies `/api` to port 8080 via `proxy.conf.json`. In
Docker, nginx does the same job. The browser therefore only ever talks to one origin, which is why
**there is no CORS configuration anywhere in this project** — the same-origin rule is never
triggered.

---

## What the app does

- **Open an account** — account number, owner, and a currency from a fixed list. Balance starts at
  zero; money can only arrive through a ledger entry, never by being asserted in a request.
- **Deposit and withdraw** — one financial event, one ledger entry, balance updated in the same
  database transaction.
- **Transfer** — one event, two entries (a debit and a credit) that net to zero. Both land or
  neither does.
- **Read a statement** — every entry for an account, oldest first, with debit and credit in
  separate columns and the running balance on the right.

Business rules enforced by the server: an account cannot go below zero, a transfer cannot be sent
to the same account, and both sides of a transfer must share a currency.

---

## API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/health` | liveness |
| `POST` | `/api/accounts` | open an account → `201` + `Location` |
| `GET` | `/api/accounts` | list accounts with balances |
| `POST` | `/api/accounts/{id}/deposits` | credit the account |
| `POST` | `/api/accounts/{id}/withdrawals` | debit the account |
| `GET` | `/api/accounts/{id}/entries` | the account's statement |
| `POST` | `/api/transfers` | move money between two accounts |

Every posting request carries a client-generated `reference` (a UUID). It is stored under a unique
constraint, so a retried request cannot record the same event twice — this is the idempotency key.

### Errors

One shape for the whole API, produced by a single `@RestControllerAdvice`:

```json
{
  "timestamp": "2026-09-06T18:31:17.468Z",
  "status": 422,
  "error": "Insufficient balance",
  "message": "Account ACC-002 has a balance of 250.2500, which is less than the requested 99999",
  "path": "/api/accounts/2/withdrawals"
}
```

| Status | When |
|---|---|
| `400` | request failed validation — adds a `fieldErrors` array naming each rejected field |
| `404` | the account does not exist |
| `409` | account number already taken, or a database constraint rejected the write |
| `422` | understood but not allowed — insufficient balance, same-account or cross-currency transfer |

The advice deliberately has **no** `@ExceptionHandler(Exception.class)`. A catch-all is resolved
before Spring's own handlers and would turn malformed JSON, an unknown path and a wrong HTTP
method into `500`s instead of the `400`, `404` and `405` they should be.

---

## Architecture

```
Browser
   │  only ever requests /api/...  (one origin, so no CORS)
   ▼
nginx ─────────────┐
   │ static files  │ /api/* proxied to backend:8080
   ▼               ▼
Angular build   Spring Boot
                   │
              controller   HTTP only: bind, validate, set the status code
                   │
               service     business rules and transaction boundaries
                   │
              repository   database access (package-private)
                   │
                   ▼
             PostgreSQL — schema owned by Flyway migrations
```

**Layered, and packaged by feature.** `account/`, `entry/`, `web/`, `health/` — each package holds
its own controller, service, repository and entity rather than the codebase being split into
`controllers/`, `services/`, `repositories/`.

**Repositories are package-private.** `AccountRepository` is not `public`, so the posting feature
physically cannot reach account data — it must ask `AccountService`. The compiler enforces the
boundary instead of a naming convention asking politely. It also means every balance change goes
through one method, so every balance change is locked.

**DTOs at the edge.** Controllers accept and return records (`CreateAccountRequest`,
`AccountResponse`, `EntryResponse`), never entities. The API contract can then change independently
of the database schema, and no entity is accidentally serialised with its lazy associations.

---

## How a request flows

Taking `POST /api/transfers` end to end:

1. **nginx** matches `/api/` and proxies to `backend:8080`, resolving the container by its Compose
   service name.
2. **`DispatcherServlet`** routes to `TransferController.transfer(...)`.
3. **Jackson** deserialises the JSON into a `TransferRequest` record.
4. **`@Valid`** runs Bean Validation *before* the method body. A failure throws, and the advice
   returns `400` with every rejected field.
5. **`PostingService.transfer`** is `@Transactional` — everything from here is one unit of work.
   It writes the `transactions` row, then asks `AccountService` to move the money.
6. **`AccountService.moveMoney`** locks both accounts with `SELECT ... FOR NO KEY UPDATE`,
   **always taking the lower account id first**, then applies the debit and the credit.
7. Two `ledger_entries` rows are written, each recording the balance it left behind.
8. **Commit.** Hibernate flushes the balance changes and both entries together.
9. The controller returns `201` with the debit entry and the credit entry.

If any step throws, the exception escapes the service — nothing catches it — and Spring rolls the
whole transaction back. The `transactions` row written in step 5 is discarded along with
everything else, so the ledger never keeps a record of an event that did not complete.

---

## Inner workings

### Money is `BigDecimal`, never a float

`NUMERIC(19,4)` in PostgreSQL, `BigDecimal` in Java. Binary floating point cannot represent 0.1
exactly, and rounding errors that are invisible in a UI are unacceptable in a ledger. The frontend
receives balances as JSON numbers and only ever *displays* them — no arithmetic happens in the
browser.

### The ledger is append-only

`LedgerEntry` has no setters and the table is never updated or deleted from. A mistake is corrected
by posting a reversing entry, so the history always says what actually happened. `amount` is always
positive; a `direction` column of `DEBIT` or `CREDIT` carries the sign.

### The balance column is a cache, and it is proved

`accounts.balance` is a *materialized derived aggregate*. The entries are canonical; the column
exists so that reading a balance does not have to scan an account's whole history. Three safeguards
keep it honest:

1. Only `Account.credit()` and `Account.debit()` can change it.
2. It is written in the same transaction as the entry that causes the change.
3. `BalanceReconciliationTest` replays the entries and asserts they sum to the stored balance.

### Concurrency is handled with pessimistic locking

`@Lock(PESSIMISTIC_WRITE)` on the account lookup. Two simultaneous withdrawals cannot both read the
same balance, both decide there is enough, and both succeed — the second waits for the first to
commit.

Transfers lock **both** accounts in ascending id order. Without a fixed order, a transfer A→B
locking A then B, racing a transfer B→A locking B then A, deadlocks: each holds what the other
needs. Ordering means both queue for the same row, so one waits and the other proceeds.

Optimistic locking was considered and rejected: it needs retry logic, and for a balance,
correctness matters more than throughput.

### Validation happens twice, on purpose

Bean Validation gives the client a clear `400`. Database `CHECK` and `UNIQUE` constraints are the
actual guarantee. The duplicate-account-number check in the service exists only to produce a good
error message — two simultaneous requests could both pass it, and the unique constraint is what
makes duplicates impossible.

### Schema changes belong to Flyway

`spring.jpa.hibernate.ddl-auto=validate`. Hibernate is never allowed to create or alter a table; it
only checks that the entities match the schema at startup. Every schema change is a reviewable,
repeatable migration in `backend/src/main/resources/db/migration`.

### Frontend

One page, one component, no router — the app has a single screen. Two services own the HTTP calls;
the component holds signals for the state and one method per endpoint. Forms use template reference
variables rather than a forms module, because the server owns validation and the screen simply
shows what the server said.

The idempotency key is generated in the browser. `crypto.randomUUID()` only exists in a secure
context, so there is a `crypto.getRandomValues` fallback for the case where the app is opened on a
plain LAN address rather than localhost.

---

## Tests

```bash
cd backend  && ./mvnw test   # needs PostgreSQL on localhost:5433
cd frontend && npm test
```

| Test | What it proves |
|---|---|
| `LedgerApplicationTests` | the context starts, so every bean wires and the entities match the schema |
| `ConcurrentWithdrawalTest` | ten threads withdraw the same balance at once; exactly one wins, and the nine rolled-back attempts leave no `transactions` rows |
| `ConcurrentTransferTest` | ten transfers in both directions at once; none deadlock and the money is conserved |
| `BalanceReconciliationTest` | replaying an account's entries reproduces its stored balance |
| `posting.service.spec.ts` | the UUID fallback produces valid v4 references outside a secure context |

The two concurrency tests were each run once with their safeguard removed, to confirm they can
actually fail. Without the lock, all ten withdrawals succeeded and 1000 was withdrawn against a
balance of 100. Without the ordering, nine of ten transfers died with PostgreSQL SQLSTATE `40P01`,
deadlock detected.

They commit for real rather than running inside a rolled-back test transaction — a test transaction
would hold the very rows the worker threads compete for, and the test would hang instead of failing.

---

## Docker setup

Three services in `docker-compose.yml`:

| Service | Image | Notes |
|---|---|---|
| `db` | `postgres:16` | named volume, so data survives a restart; healthcheck uses `pg_isready` |
| `backend` | built from `backend/Dockerfile` | waits for the database to be *healthy*, not merely started |
| `frontend` | built from `frontend/Dockerfile` | nginx serving the build and proxying `/api` |

**Both images are multi-stage.** The backend compiles with a full JDK and runs on a JRE, so no
compiler, Maven or source ends up in the runtime image, and it runs as a non-root user. The frontend
builds with Node and runs on nginx, so no Node and no `node_modules` ship.

In both Dockerfiles the dependency manifest is copied and installed *before* the source, so Docker
only re-downloads dependencies when `pom.xml` or `package-lock.json` changes rather than on every
code edit.

nginx serves `try_files $uri $uri/ /index.html`, so refreshing on any path returns the app instead
of a 404.

---

## Project layout

```
mini-transaction-ledger/
├── docker-compose.yml
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/ekramul/ledger/
│       │   ├── account/    entity, service, controller, repository, exceptions, DTOs
│       │   ├── entry/      transactions and ledger entries, posting and transfer endpoints
│       │   ├── health/     liveness endpoint
│       │   └── web/        ApiError and the global exception handler
│       ├── main/resources/db/migration/   V1 and V2 Flyway migrations
│       └── test/java/                     context, concurrency and reconciliation tests
└── frontend/
    ├── Dockerfile
    ├── nginx.conf
    ├── proxy.conf.json
    └── src/app/            component, account and posting services
```
