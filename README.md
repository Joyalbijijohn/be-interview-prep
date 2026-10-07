# be-interview-prep

Spring Boot 3.5 (Java 17, Maven, H2 in-memory) backend interview assignment.

| # | Question | PR |
|---|----------|----|
| 1 | Task Manager API | [#1](https://github.com/Joyalbijijohn/be-interview-prep/pull/1) |
| 2 | URL Shortener | [#2](https://github.com/Joyalbijijohn/be-interview-prep/pull/2) |
| 3 | Authentication & Roles | [#3](https://github.com/Joyalbijijohn/be-interview-prep/pull/3) |
| 4 | Product Catalog | [#4](https://github.com/Joyalbijijohn/be-interview-prep/pull/4) |
| 5 | Order Service | [#5](https://github.com/Joyalbijijohn/be-interview-prep/pull/5) |

Video:

## Run

```
export JWT_SECRET=$(openssl rand -base64 48)
export ADMIN_EMAIL=admin@example.com
export ADMIN_PASSWORD=<choose-a-password>
./mvnw spring-boot:run
```

`JWT_SECRET` (at least 32 characters) is required. `ADMIN_EMAIL` and `ADMIN_PASSWORD` are optional and create an ADMIN user on startup. The app listens on `http://localhost:8080`.

## Test

```
./mvnw clean verify
```

Tests use their own signing key from `src/test/resources/config/application.properties`; no environment variables are needed.

## Authentication

Register and log in to get a Bearer token (valid for 15 minutes). Every endpoint except register, login and the short-link redirect requires it.

```
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"Passw0rd123"}'

TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"Passw0rd123"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['accessToken'])")

curl localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"
```

## Endpoints

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/auth/register` | Public. Creates a USER. |
| POST | `/api/auth/login` | Public. Returns `accessToken`, `expiresIn` (900). |
| GET | `/api/users/me` | Own profile. |
| GET | `/api/users` | ADMIN only. |
| POST, GET | `/api/tasks` | Create; list with optional `?status=TO_DO\|IN_PROGRESS\|DONE`. |
| GET, PUT, DELETE | `/api/tasks/{id}` | |
| POST | `/api/links` | Body `{"url": "...", "expiresAt": "<ISO instant, optional>"}`. |
| GET | `/{code}` | Public. 302 redirect, counts the visit. 404 unknown, 410 expired. |
| GET | `/api/links/{code}/stats` | Original URL, visit count, created date. |
| GET | `/api/products` | `page`, `size` (max 100), `sort=field,dir`, `category`, `minPrice`, `maxPrice`, `inStock=true`, `q`. |
| GET | `/api/products/{id}` | Cached. |
| PUT, DELETE | `/api/products/{id}` | ADMIN only. |
| POST | `/api/orders` | Requires `Idempotency-Key` header. Body `{"items":[{"productId":1,"quantity":2}]}`. 201 created, 200 replay, 409 insufficient stock. |
| POST | `/api/orders/{id}/cancel` | Returns the stock. Owner only. |

Errors are always JSON: `{"timestamp","status","error","message","fieldErrors"}`.

## Design notes

- **Q1:** one `@RestControllerAdvice` maps validation, not-found, malformed input and unexpected errors to a single error format.
- **Q2:** each submission creates a new link with its own stats. Visits are counted with an atomic `visit_count + 1` update. Redirects use 302 so every visit reaches the server.
- **Q3:** stateless HS256 JWT with a 15-minute expiry and zero clock skew. Passwords are BCrypt-hashed. The signing key comes from the environment.
- **Q4:** filters are built with JPA Specifications. Single-product lookups are cached with Caffeine and evicted on update, delete and stock changes from orders. The cache test counts real SQL statements.
- **Q5:** stock is reserved with a conditional `UPDATE ... WHERE stock >= qty` inside one transaction, so orders are all-or-nothing and cannot oversell. Retries are recognised by a unique `(user, Idempotency-Key)` constraint.
