# lease-occupancy-service

Owns lease and occupancy records for the Apartment Management System (Group 2 — Property & Occupancy).
See [AGENTS.md](AGENTS.md) for full project and service context, and
[Project_A_JWT_Authentication_and_Security_Standard.md](Project_A_JWT_Authentication_and_Security_Standard.md)
for the org-wide auth model this service implements.

## Stack

- Java 21, Spring Boot 3.5.x (Web, Data JPA, Validation, Actuator, Flyway)
- MySQL (`lease_db`), owned exclusively by this service
- RS256 JWT (jjwt) for Gateway-issued token verification and outbound Service JWT signing
- Maven (via the included wrapper — no local Maven install required)

## Configuration

Copy [.env.example](.env.example) to `.env` (gitignored) for Docker Compose and fill in real
values. For a direct Maven run, export the same variables in your shell; Spring Boot does not
load `.env` automatically. Every service and internal endpoint requires a
valid Gateway-issued JWT; without `GATEWAY_JWT_PUBLIC_KEY` set, the service generates an
ephemeral dev keypair at boot and will reject every real Gateway-signed token (a clear warning
is logged when this happens).

## Running locally

```bash
./mvnw spring-boot:run
```

The service starts on **port 8084** (`http://localhost:8084/api/v1`). It expects a MySQL
instance (see `DB_*` env vars, defaults to `localhost:3308/lease_db`) and, for real requests,
a reachable Gateway whose public key is configured.

## Running tests

```bash
./mvnw test
```

Tests run against an in-memory H2 database (MySQL compatibility mode) and a fixed test
JWT keypair (`TestJwtTokens`), so no external database or Gateway is required locally or in CI.

## Docker

From the parent `Backend` directory in PowerShell:

```powershell
docker compose -f .\lease-occupancy-service\docker-compose.yml up -d --build
docker compose -f .\lease-occupancy-service\docker-compose.yml ps
Invoke-RestMethod http://localhost:8084/actuator/health
docker compose -f .\lease-occupancy-service\docker-compose.yml exec lease_db mysql -ulease_service -please_password lease_db -e "SHOW TABLES; SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Compose starts this service on port 8084 and its dedicated MySQL database on host port 3308.
The MySQL data lives in a named Docker volume; `docker compose down` retains it. Integration
with the shared Gateway, property, and identity services requires their routes and JWT keys.

## Group 2 contract

The property service exposes UUID unit IDs through its API while keeping numeric database IDs
internally for ownership records. This service calls its Gateway-routed
`/api/v1/internal/units/{unitId}` and `/capacity` endpoints, and updates the unit status on
lease activation or closure. Register a physical occupancy after activating its lease;
residency validation checks those occupancy records against a currently effective lease.
Lease status changes keep an actor, reason, and timestamp in `lease_status_history`.
Future leases remain drafts or pending activation until their start date; activation is
accepted only during the agreed period so the property unit is not marked occupied early.

## Integration limits

The local automated suite uses H2 and mock HTTP responses. Before release, run both Group 2
services against MySQL through the Gateway and verify the Flyway migrations, JWT roles, and
status changes end to end. The maintenance relocation and billing notification workflow
still needs agreed Group 3/4 contracts. If a remote unit status update succeeds but the
lease database transaction later fails, reconcile the two services before retrying; there
is no distributed transaction coordinator.

## Status

See [AGENTS.md](AGENTS.md) §5 for what's implemented, §6 for known gaps, and §8 for the
JWT/security implementation checklist.
