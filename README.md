# lease-occupancy-service

Owns lease contracts and physical occupancy records for the Apartment Management System (Group 2 — Property & Occupancy).
Follows the canonical architecture defined in `LEASE-OCCUPANCY-SERVICE.md`, `PROJECT-A-CONTRACT-DECISIONS.md`, `PROJECT-A-GLOBAL-API-STANDARD.md`, `PROJECT-A-JWT-SECURITY-STANDARD.md`, and `PROJECT-A-CROSS-SERVICE-API-REGISTRY.md`.

## Stack

- Java 21, Spring Boot 4.1.1 (Web MVC, Data JPA, Validation, Actuator, Flyway)
- Maven (`kln.ams:lease-occupancy-service`, package: `kln.ams.leaseoccupancy`)
- MySQL (`lease_occupancy_db`), owned exclusively by this service
- RS256 JWT (jjwt) for Gateway User JWT verification and outbound Service JWT signing
- Maven wrapper included

## Canonical 14 Domain Endpoints

### Public / User Endpoints (User JWT)
1. **LEASE-001**: `POST /api/v1/leases` — Create a lease (`APARTMENT_MANAGER`, `SYSTEM_ADMINISTRATOR`)
2. **LEASE-002**: `GET /api/v1/leases` — Search/list leases (`APARTMENT_MANAGER`, `SYSTEM_ADMINISTRATOR`, `OWNER`, `TENANT_RESIDENT`)
3. **LEASE-003**: `GET /api/v1/leases/{leaseId}` — Read lease by ID (Management, authorized owner/occupant)
4. **LEASE-004**: `GET /api/v1/leases/{leaseId}/history` — Read lease status history (Management, authorized owner/occupant)
5. **LEASE-005**: `GET /api/v1/leases/units/{unitId}` — Lease history for unit (`APARTMENT_MANAGER`, `SYSTEM_ADMINISTRATOR`, authorized owner)
6. **LEASE-006**: `PATCH /api/v1/leases/{leaseId}/status` — Change lease status (`APARTMENT_MANAGER`, `SYSTEM_ADMINISTRATOR`)
7. **LEASE-008**: `POST /api/v1/occupancies` — Register physical move-in/occupancy (`APARTMENT_MANAGER`, `SYSTEM_ADMINISTRATOR`)
8. **LEASE-009**: `GET /api/v1/occupancies/units/{unitId}` — List occupants by unit (`APARTMENT_MANAGER`, `SYSTEM_ADMINISTRATOR`, authorized owner/resident)
9. **LEASE-010**: `GET /api/v1/occupancies/residents/{residentId}` — Resident occupancy history (Management, authorized resident)
10. **LEASE-011**: `PATCH /api/v1/occupancies/{occupancyId}/status` — Record move-out/deactivate occupancy (`APARTMENT_MANAGER`, `SYSTEM_ADMINISTRATOR`)
11. **LEASE-012**: `GET /api/v1/units/{unitId}/active-occupancy` — Query active occupancy for unit (`APARTMENT_MANAGER`, `SYSTEM_ADMINISTRATOR`, authorized owner/resident)

### Internal Service Provider Endpoints (Service JWT)
12. **LEASE-INT-001**: `GET /api/v1/internal/units/{unitId}/occupancy` — Authoritative occupancy state for unit
13. **LEASE-INT-002**: `GET /api/v1/internal/units/{unitId}/occupants` — Authoritative current occupants for unit
14. **LEASE-INT-003**: `GET /api/v1/internal/users/{userId}/occupancy` — Authoritative occupancy relationship for user

## Configuration

Copy [.env.example](.env.example) to `.env` (gitignored) for Docker Compose and fill in real
values. For a direct Maven run, export the same variables in your shell; Spring Boot does not
load `.env` automatically. Every service and internal endpoint requires a
valid Gateway-issued JWT. Startup fails if `GATEWAY_JWT_PUBLIC_KEY` or
`SERVICE_JWT_PRIVATE_KEY` is missing. Configure both through deployment secrets.

## Running locally

```bash
./mvnw spring-boot:run
```

The service starts on **port 8084** (`http://localhost:8084/api/v1`). It expects a MySQL
instance (see `DB_*` env vars, defaults to `localhost:3308/lease_occupancy_db`) and, for real requests,
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
docker compose -f .\lease-occupancy-service\docker-compose.yml exec lease_db mysql -u lease_service -p lease_occupancy_db
```

Compose starts this service on port 8084 and its dedicated MySQL database on host port 3308.
The MySQL data lives in a named Docker volume; `docker compose down` retains it. Integration
with the shared Gateway, property, and identity services requires their routes and JWT keys.

## Group 2 contract

The property service exposes UUID unit IDs through its API. This service calls its Gateway-routed
`/api/v1/internal/units/{unitId}/validate` endpoint for unit state and capacity and
`/api/v1/internal/units/{unitId}/ownership` for current ownership. The fixed property contract
has no unit status update route, so lease activation and closure do not change property status.
Owner access to unit lease history returns dependency unavailable until Resident Management's `RES-INT-002`
response defines how a JWT user ID maps to its resident profile IDs. Manager access remains available.
Register a physical occupancy after activating its lease;
occupancy validation checks physical occupant records against a currently effective lease.
Lease status changes keep an actor, reason, and timestamp in `lease_status_history`.
Future leases remain drafts or pending activation until their start date; activation is
accepted only during the agreed period.

## Integration limits

The local automated suite uses H2 and mock HTTP responses. A local MySQL 8 smoke check
applied Flyway migrations V1–V3 and returned healthy actuator and OpenAPI responses; live
cross-service integration remains unverified. Before release, run authenticated requests through
the Gateway and verify JWT roles and status changes end to end. The Resident Management
relationship response contract is needed to enable owner/resident scope and map user IDs
to resident IDs. The service contract does not define a maintenance relocation or billing
notification workflow, so neither is part of this service's v1 domain API.

## Status

See [AGENTS.md](AGENTS.md) §5 for what's implemented, §6 for known gaps, and §8 for the
JWT/security implementation checklist.
