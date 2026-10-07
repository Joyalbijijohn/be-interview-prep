# be-interview-prep

Spring Boot 3 (Java 17) backend interview assignment.

## Run

```
export JWT_SECRET=$(openssl rand -base64 48)
export ADMIN_EMAIL=admin@example.com
export ADMIN_PASSWORD=<choose-a-password>
./mvnw spring-boot:run
```

`JWT_SECRET` (at least 32 characters) is required. `ADMIN_EMAIL` and `ADMIN_PASSWORD` are optional and create an ADMIN user on startup.

## Test

```
./mvnw clean verify
```

| # | Question | PR |
|---|----------|----|
| 1 | Task Manager API | |
| 2 | URL Shortener | |
| 3 | Authentication & Roles | |
| 4 | Product Catalog | |
| 5 | Order Service | |

Video:
