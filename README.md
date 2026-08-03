# Booking System with Race Condition Protection

A Spring Boot backend where multiple users can attempt to book the same slot
concurrently, but the system guarantees a slot is booked **exactly once**,
even under high concurrency — using **database-level optimistic locking**,
not in-memory locks.

---

## Tech Stack

| Concern            | Choice                                   |
|---------------------|-------------------------------------------|
| Language / Framework | Java 17, Spring Boot 3.3.4               |
| Database            | H2 (file-based, persistent)               |
| Persistence         | Spring Data JPA / Hibernate                |
| Auth                | JWT (stateless), Spring Security, role-based |
| Validation          | Jakarta Bean Validation (`@Valid`)         |
| Build tool           | Maven                                     |
| Test                | JUnit 5, Mockito, MockMvc, AssertJ, JaCoCo |
| Code quality         | SonarQube (Maven plugin, SonarCloud-ready) |

---

## Project Structure

```
src/main/java/com/assignment/booking
├── BookingSystemApplication.java
├── config/           SecurityConfig
├── controller/        AuthController, SlotController, BookingController, AdminBookingController
├── dto/               Request/response DTOs with bean validation
├── entity/            User, Slot (has @Version), Booking
├── enums/             Role, BookingStatus, SlotStatus
├── exception/         Custom exceptions + GlobalExceptionHandler (@RestControllerAdvice)
├── repository/        Spring Data JPA repositories
├── security/          JwtService, JwtAuthFilter, UserDetailsServiceImpl
└── service/impl/      AuthServiceImpl, SlotServiceImpl, BookingServiceImpl
```

---

## Setup & Run Instructions

### Prerequisites
- Java 17+
- Maven 3.8+ (or use the included wrapper if you add one)

### Run locally
```bash
mvn clean install
mvn spring-boot:run
```
The app starts on `http://localhost:8080`.

H2 console (dev/debug only): `http://localhost:8080/h2-console`
JDBC URL: `jdbc:h2:file:./data/bookingdb`, user `sa`, empty password.
(A `data/bookingdb.mv.db` file is created on first run and **persists across restarts**.)

### Run tests + coverage
```bash
mvn clean test
```
JaCoCo report: `target/site/jacoco/index.html` (build fails if line coverage < 80%, enforced via the `jacoco-check` goal).

### Code Quality (SonarQube)
A `docker-compose.yml` is included to easily spin up a local SonarQube server:
```bash
docker-compose up -d
```
Once it starts (on `http://localhost:9000`), run the analysis:
```bash
mvn clean verify sonar:sonar -Dsonar.login=admin -Dsonar.password=admin
```

### Run with Docker
```bash
docker build -t booking-system .
docker run -p 8080:8080 -e JWT_SECRET=<your-base64-secret> booking-system
```

### Environment variables
| Variable | Purpose | Default |
|---|---|---|
| `JWT_SECRET` | Base64-encoded HMAC-SHA256 signing key | dev placeholder in `application.yml` — **override in any real deployment** |
| `JWT_EXPIRATION_MS` | Token lifetime in ms | `3600000` (1 hour) |

---

## API Flow

Authentication isn't in the original spec's endpoint list, but the spec's
own security rules ("only authenticated users can book slots") require it,
so two endpoints were added: `POST /auth/register` and `POST /auth/login`.
Both return a JWT that must be sent as `Authorization: Bearer <token>` on
every subsequent request.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/auth/register` | public | Create a USER or ADMIN account |
| POST | `/auth/login` | public | Get a JWT |
| POST | `/slots` | ADMIN | Create a bookable slot |
| GET | `/slots` | USER, ADMIN | List all slots (available + booked) |
| POST | `/bookings` | USER | Book a slot (`{ "slotId": 1 }`) |
| POST | `/bookings/{id}/cancel` | USER | Cancel **your own** booking |
| POST | `/admin/bookings/{id}/cancel` | ADMIN | Cancel **any** booking |

### Interactive API Documentation (Swagger UI)
The application includes Swagger UI for easy, interactive API testing. 
1. Start the application.
2. Visit **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**.
3. Use the **Authorize** button to paste your JWT token and test secured endpoints directly from the browser.

### Example flow
```bash
# 1. Register an admin
curl -X POST localhost:8080/auth/register -H "Content-Type: application/json" \
  -d '{"username":"admin1","password":"password123","role":"ADMIN"}'

# 2. Login to get token (or reuse the one from register)
TOKEN=$(curl -s -X POST localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin1","password":"password123"}' | jq -r .token)

# 3. Create a slot
curl -X POST localhost:8080/slots -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"startTime":"2099-01-01T10:00:00","endTime":"2099-01-01T11:00:00"}'

# 4. Register + login a normal user, then book the slot
curl -X POST localhost:8080/bookings -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" -d '{"slotId":1}'
```

All errors (validation, not-found, conflict, forbidden, unauthorized) return
a consistent JSON body from `GlobalExceptionHandler`:
```json
{
  "timestamp": "2026-08-02T10:15:30",
  "status": 409,
  "error": "Conflict",
  "message": "Slot 1 is already booked",
  "path": "/bookings"
}
```

---

## Concurrency Design (the core of the assignment)

### Chosen strategy: **Optimistic Locking**

`Slot` has a `@Version` column. Hibernate automatically:
1. Includes `version` in the `WHERE` clause of every `UPDATE`:
   `UPDATE slot SET status=?, version=? WHERE id=? AND version=<version read>`
2. Increments it on every successful update.
3. Throws `OptimisticLockException` (wrapped by Spring as
   `ObjectOptimisticLockingFailureException`) if the `UPDATE` matches zero
   rows — meaning someone else already changed that row since we read it.

**Why optimistic over pessimistic here:** booking is a low-contention,
high-read/low-write workload per slot (many users *view* slots, only one
*wins* the booking). Optimistic locking avoids holding a DB row lock for the
duration of a transaction and scales better under read-heavy load. It also
maps naturally onto "let the loser find out via a clear error" (409), which
is exactly what the spec asks for ("user must receive a clear failure
response if slot is already booked"). Pessimistic locking (`SELECT ... FOR
UPDATE`) would also satisfy the requirement and was the other option
allowed by the spec — it trades some scalability for making conflicts
*wait* instead of *fail fast*, which is a defensible choice too, just not
the one made here.

### Where it lives: `BookingServiceImpl.bookSlot()`

```java
@Transactional
public BookingResponse bookSlot(BookingRequest request, String username) {
    Slot slot = slotRepository.findById(request.getSlotId())...;  // reads current version
    if (slot.getStatus() == SlotStatus.BOOKED) {
        throw new BookingConflictException(...);                  // fast path, no race needed
    }
    slot.setStatus(SlotStatus.BOOKED);
    slotRepository.save(slot);                                    // version-checked UPDATE
    bookingRepository.save(new Booking(...));                     // only reached if the update won
    return ...;
}
```

### Transaction boundaries

The entire method is one `@Transactional` unit. The slot status flip and
the booking row insert either **both** commit or **both** roll back — there
is no window where a slot is `BOOKED` with no corresponding `Booking` row,
or a `Booking` exists against a slot still marked `AVAILABLE`. If the
optimistic-lock check fails at flush/commit time, the whole transaction
rolls back automatically; no partial state is ever persisted. Cancellation
(`cancelOwnBooking` / `cancelAnyBooking`) follows the same pattern: booking
→ `CANCELLED` and slot → `AVAILABLE` happen in one transaction.

### Why this survives restarts / isn't an in-memory lock

The "lock" is the `version` column stored in the database row itself, not a
`synchronized` block or in-memory map in the JVM. Kill and restart the
application mid-load-test and the guarantee still holds, because the check
is enforced by the database's row state on every `UPDATE`, and it holds
just as well across multiple app instances behind a load balancer, which an
in-process lock never could.

### Example concurrent scenario (and how it's actually tested)

`BookingConcurrencyIntegrationTest` spins up 20 threads, all bound to real
DB connections, all racing to book the *same* slot using a `CountDownLatch`
starting gun so they hit `bookSlot()` at effectively the same instant. The
test asserts:
- exactly **1** succeeds
- the other **19** fail with a conflict (either the fast-path check or the
  optimistic-lock exception — both are legitimate outcomes depending on
  thread interleaving)
- the database ends up consistent: slot `BOOKED`, exactly one `ACTIVE`
  booking row, no orphaned/partial rows

This is a real integration test against H2 (no mocking of the locking
mechanism), so it exercises the actual Hibernate version-check behavior.

---

## Assumptions & Design Decisions

- **Auth wasn't specified beyond "authenticated users"** — implemented as
  stateless JWT with `USER`/`ADMIN` roles assigned at registration, since
  the spec has no separate "assign role" admin flow and this keeps the
  assignment self-contained/testable without a seed script.
- **Slot times must be in the future** (`@Future` validation) — a slot
  starting in the past isn't bookable in any real scenario; this was added
  as a sensible validation rule beyond the literal spec text.
- **`GET /slots` returns all slots** (available and booked) as literally
  specified, rather than filtering — the client can filter by `status` if
  it only wants available ones.
- **Cancelling a slot makes it `AVAILABLE` again** (not a new state) —
  the spec only defines `AVAILABLE`/`BOOKED` for `Slot`, so cancellation is
  modeled as returning to the `AVAILABLE` state rather than introducing an
  unspecified third status.
- **No in-memory locks or `synchronized` blocks anywhere**, per the
  spec's explicit constraint — concurrency safety comes entirely from the
  `@Version` column + transaction boundaries.
- **JWT secret has a dev-only default** in `application.yml` for
  convenience; production deployments must override `JWT_SECRET` via
  environment variable (see Dockerfile).

---

## Test Coverage

- **Unit tests** (Mockito): `BookingServiceImplTest`, `SlotServiceImplTest`,
  `AuthServiceImplTest`, `JwtServiceTest` — business logic in isolation.
- **Controller tests** (`@WebMvcTest` + MockMvc): request validation,
  status codes, JSON shape for every endpoint.
- **Integration tests** (`@SpringBootTest`, real H2, real Spring Security
  filter chain):
  - `BookingConcurrencyIntegrationTest` — the race-condition proof above.
  - `SecurityRoleIntegrationTest` — role enforcement end-to-end with real
    JWTs (401 unauthenticated, 403 wrong role, 201/200 correct role).

Run `mvn clean test` to execute everything and generate the JaCoCo report
(target: ≥80% line coverage, enforced by the build).

---

## Deployment (optional)

A `Dockerfile` is included (multi-stage Maven build → JRE runtime image).
To deploy on Render/Railway: point the service at this repo, it auto-detects
the `Dockerfile`, set the `JWT_SECRET` env var, and expose port `8080`.

## Deployment is done 
Live API:
https://booking-system-oc4z.onrender.com

Swagger:
https://booking-system-oc4z.onrender.com/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config

