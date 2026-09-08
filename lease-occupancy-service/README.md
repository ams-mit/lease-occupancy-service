# lease-occupancy-service

Owns lease and occupancy records for the Apartment Management System (Group 2 — Property & Occupancy).
See [../AGENTS.md](../AGENTS.md) for full project and service context.

## Stack

- Java 21, Spring Boot 3.5.x (Web, Data JPA, Validation, Actuator)
- PostgreSQL (`lease_db`), owned exclusively by this service
- Maven (via the included wrapper — no local Maven install required)

## Running locally

```bash
./mvnw spring-boot:run
```

The service starts on **port 8084** (`http://localhost:8084/api/v1`). It expects a PostgreSQL
instance reachable via the `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD`
environment variables (defaults: `localhost:5432/lease_db`, see `application.yml`).

## Running tests

```bash
./mvnw test
```

Tests run against an in-memory H2 database (PostgreSQL compatibility mode) so no external
database is required locally or in CI.

## Docker

```bash
docker build -t lease-occupancy-service .
docker run -p 8084:8084 --env DB_HOST=host.docker.internal lease-occupancy-service
```

Intended to run as part of the project's shared `docker-compose.yml` alongside its own
`lease_db` Postgres container and the other AMS microservices.

## Status

Repository scaffold only (Sprint 2 runway). No entities, controllers, or Flyway migrations
have been implemented yet — see [../AGENTS.md](../AGENTS.md) §3 for the planned domain
entities, business rules, and endpoint contract.
