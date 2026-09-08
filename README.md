# lease-occupancy-service

Owns lease and occupancy records for the Apartment Management System (Group 2 — Property & Occupancy).
See [AGENTS.md](AGENTS.md) for full project and service context, and
[Project_A_JWT_Authentication_and_Security_Standard.md](Project_A_JWT_Authentication_and_Security_Standard.md)
for the org-wide auth model this service implements.

## Stack

- Java 21, Spring Boot 3.5.x (Web, Data JPA, Validation, Actuator, Flyway)
- PostgreSQL (`lease_db`), owned exclusively by this service
- RS256 JWT (jjwt) for Gateway-issued token verification and outbound Service JWT signing
- Maven (via the included wrapper — no local Maven install required)

## Configuration

Copy [.env.example](.env.example) to `.env` (gitignored) and fill in real values — database
credentials and the JWT keys described there. Every service and internal endpoint requires a
valid Gateway-issued JWT; without `GATEWAY_JWT_PUBLIC_KEY` set, the service generates an
ephemeral dev keypair at boot and will reject every real Gateway-signed token (a clear warning
is logged when this happens).

## Running locally

```bash
./mvnw spring-boot:run
```

The service starts on **port 8084** (`http://localhost:8084/api/v1`). It expects a PostgreSQL
instance (see `DB_*` env vars, defaults to `localhost:5432/lease_db`) and, for real requests,
a reachable Gateway whose public key is configured.

## Running tests

```bash
./mvnw test
```

Tests run against an in-memory H2 database (PostgreSQL compatibility mode) and a fixed test
JWT keypair (`TestJwtTokens`), so no external database or Gateway is required locally or in CI.

## Docker

```bash
docker build -t lease-occupancy-service .
docker run -p 8084:8084 --env-file .env lease-occupancy-service
```

Intended to run as part of the project's shared `docker-compose.yml` alongside its own
`lease_db` Postgres container and the other AMS microservices.

## Status

See [AGENTS.md](AGENTS.md) §5 for what's implemented, §6 for known gaps, and §8 for the
JWT/security implementation checklist.
