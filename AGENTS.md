# AGENTS.md — Apartment Management System (AMS)

Context file for AI-assisted development on this project. This is a **group project** with four teams building independent microservices behind a shared API Gateway. This document captures the project-wide architecture and, in detail, the service owned by this developer: **`lease-occupancy-service`** (Group 2).

Role of the person driving this repo: **Backend Developer**, responsible for `lease-occupancy-service`. See §6 for current implementation status and §7 for the repo/workflow conventions.

---

## 1. System Overview

**Goal:** Replace manual, spreadsheet/paper-based property operations with a role-based digital platform for property managers, owners, residents, finance staff, and operations personnel.

**Architecture style:** Microservices behind a central API Gateway, one shared React frontend, strict per-service database ownership.

### Global Technical Stack

| Layer | Technology |
|---|---|
| Frontend | React + TypeScript + Redux (single shared app, modular feature areas per team) |
| Backend | Spring Boot, REST APIs, strict **Controller → Service → Repository** layering |
| ORM | Hibernate / JPA |
| Security | Centralized API Gateway as single entry point; JWT Bearer tokens for role-based auth |
| Database | Each microservice owns an isolated schema/database — no direct cross-service DB access, ever. All inter-service data access is via REST APIs. |
| Local dev | Docker Compose (all services + DBs run together for integration testing) |
| API docs | OpenAPI / Swagger per service |
| API testing | Postman collections + automated tests |

### Non-negotiable architectural rules

1. **No cross-service DB access.** Ever. All data belonging to another service is fetched via that service's REST API.
2. **Controller-Service-Repository boundary** must be respected in every Spring Boot service.
3. **Gateway is the only public entry point** for the frontend; internal service-to-service calls go over the private network and skip JWT validation (see §5C).
4. Every request must propagate `X-Request-ID` for cross-service tracing.
5. Every response uses the **standard envelope** (see §4) — success and error shapes are shared across all services in this project.

---

## 2. Team / Service Ownership Map

| Group | Domain | Services owned |
|---|---|---|
| Group 1 | Identity & Residents | `identity-access-service`, `resident-management-service` |
| **Group 2** | **Property & Occupancy** | **`property-unit-service`**, **`lease-occupancy-service`** ← this developer's service |
| Group 3 | Billing & Payments | `billing-payment-service`, `utility-charge-service` |
| Group 4 | Operations & Community | `operations-service`, `community-service` |

Group 2 sibling relationship: `property-unit-service` owns the physical inventory (buildings/floors/units, capacity limits, unit status). `lease-occupancy-service` owns contractual/residency data and must call `property-unit-service`'s API rather than reading its DB.

---

## 3. This Service: `lease-occupancy-service`

**Purpose:** The core gateway for validating *who resides in which unit and under what contractual parameters.*

### Base settings

| Setting | Value |
|---|---|
| Service name | `lease-occupancy-service` |
| Local base URL | `http://localhost:8084/api/v1` |
| Env var binding | `LEASE_SERVICE_URL` |
| Database | `lease_db` (dedicated PostgreSQL schema, isolated from sibling DBs) |
| Domain entities (indicative) | `Lease`, `Occupant`, `OccupancyStatus` |

Because user/resident profiles belong to Group 1, this service stores **synthetic foreign IDs only** (`tenantId`, `residentId`, etc.) and resolves profile details on demand via REST calls to Group 1's services — never stores denormalized copies of their data as source of truth.

### Core business rules (enforced in the Service layer, not the Controller)

1. **Date-Overlap Check (standard units):** Standard units are strict 1-to-1 occupancy. Block creating/activating a lease whose date range overlaps an existing *active* lease on the same unit.
2. **Multi-Occupancy Capacity Check (co-living units):** For units supporting multiple concurrent leases (e.g. dorms), cross-reference the unit's `capacity_limit` (sourced from `property-unit-service`) and block any new roommate activation that would breach it.
3. **Activation Prerequisites:** A lease/occupancy can only go `ACTIVE` if:
   - the target unit exists (validated via `property-unit-service`),
   - the tenant/resident parties are valid (validated via Group 1's identity/resident APIs),
   - dates don't conflict (rule 1/2 above).
4. **Maintenance Relocation Protocol:** When a unit transitions to "under maintenance" (triggered by Group 4/Operations):
   - soft-terminate active occupant records for that unit,
   - create a relocation record targeting an available unit of the same type,
   - emit a notification event to Billing (Group 3).

### Endpoint contract (13 endpoints)

Status legend: ✅ implemented · ⏳ not yet built.

**A. Contractual Leases (public, gateway-routed)**

| | Method | Path | Auth | Notes |
|---|---|---|---|---|
| ✅ | POST | `/api/v1/leases` | Manager* | Validates tenant ID with Group 1; checks schedule conflicts |
| ✅ | GET | `/api/v1/leases` | Manager* | Filterable by status and an active-on date, paginated |
| ⏳ | GET | `/api/v1/leases/{leaseId}` | Manager or Resident | Resident access only if their `userId` appears as occupant/tenant on this lease |
| ⏳ | GET | `/api/v1/leases/units/{unitId}` | Manager or Owner | Chronological lease history for a unit |
| ✅ | PATCH | `/api/v1/leases/{leaseId}/status` | Manager* | Activate / terminate / complete, with transition + re-overlap checks |

**B. Physical Occupancies (public, gateway-routed)** — ⏳ not started (no `Occupant` entity yet)

| | Method | Path | Auth | Notes |
|---|---|---|---|---|
| ⏳ | POST | `/api/v1/occupancies` | Manager | Registers physical arrival under active lease/ownership |
| ⏳ | GET | `/api/v1/occupancies/units/{unitId}` | All roles | Current occupants of a unit |
| ⏳ | GET | `/api/v1/occupancies/residents/{residentId}` | All roles | Occupancy history for a resident |
| ⏳ | PATCH | `/api/v1/occupancies/{occupancyId}/status` | Manager | Soft-deactivate (→ `INACTIVE`), never hard-delete |

**C. Internal service-to-service (private network, no JWT)**

| | Method | Path | Consumer | Purpose |
|---|---|---|---|---|
| ✅ | GET | `/api/v1/internal/occupancies/active-billing` | `billing-payment-service` (Group 3) | Active units + billing targets for recurring invoicing |
| ✅ | GET | `/api/v1/internal/occupancies/validate` | `operations-service` (Group 4) | Check tenant actively resides in unit before facility booking / maintenance request. Currently backed by `Lease` (tenant-of-record); should switch to `Occupant` once B exists |

**D. System operations (public/actuator)**

| | Method | Path | Notes |
|---|---|---|---|
| ✅ | GET | `/actuator/health` | App + DB health, used by Docker Compose |
| ✅ | GET | `/actuator/info` | Deployment metadata for Gateway |
| ✅ | GET | `/swagger-ui.html`, `/v3/api-docs` | SpringDoc, split into `public`/`internal` groups |

*Manager-role enforcement is **not yet wired in** — no JWT filter/library exists in this service yet (see §6).

### Cross-team integration

- **← Group 1:** calls `GET /api/v1/internal/users/{userId}/validate` to verify account status during lease setup.
- **↔ Group 2 sibling (`property-unit-service`):** checks unit config/capacity limits; pushes "occupied"/"under maintenance" status transitions.
- **→ Group 3:** supplies billing target arrays for automated utility charges/invoicing.
- **→ Group 4:** supplies residency validation so Operations can block unverified users from bookings/complaints.
- **Gateway compliance:** parse incoming `X-Request-ID`; return the standard response envelope on every endpoint.

---

## 4. Shared API Standard (project-wide contract)

Full detail in [API-STANDARD-v1.md](API-STANDARD-v1.md). Key points every endpoint in this service must follow:

- Base path `/api/v1`, kebab-case URLs, plural resource nouns, camelCase JSON.
- Headers: `Content-Type: application/json`, `Authorization: Bearer <JWT>` (except internal endpoints), `X-Request-ID: <UUID>`.
- **Success envelope:**
  ```json
  { "success": true, "message": "...", "data": {}, "pagination": {}, "timestamp": "...", "requestId": "..." }
  ```
- **Error envelope:**
  ```json
  { "success": false, "message": "...", "error": { "code": "...", "details": null }, "timestamp": "...", "requestId": "..." }
  ```
- IDs are UUIDs; dates/times are ISO-8601; enums are UPPERCASE.
- Pagination default `page=0&size=20`, max `size=100`.
- Business error codes relevant to this service (from the registry): `LEASE_NOT_FOUND`, `OCCUPANCY_CONFLICT`, `INVALID_LEASE_STATUS`, plus general codes `VALIDATION_ERROR`, `RESOURCE_NOT_FOUND`, `DUPLICATE_RESOURCE`, `BUSINESS_RULE_VIOLATION`, `DEPENDENCY_UNAVAILABLE`, `INTERNAL_SERVER_ERROR`.
- Downstream dependency failures must return `503 DEPENDENCY_UNAVAILABLE` — never a fake success with empty data. A failed dependency call must not leave local records partially created/updated.
- Recommended inter-service timeouts: 2s connect / 5s read.
- Centralized `@RestControllerAdvice` exception handling; DTO-level `@Valid` validation; no stack traces or secrets in error responses.
- Every endpoint needs OpenAPI/Swagger docs, a Postman example, automated tests, and an entry in the API Contract Registry.

---

## 5. Status

Implemented (on `main`, all pushed to GitHub):

- Spring Boot 3.5.16 / Java 21 project scaffold, port `8084`, Controller-Service-Repository package layout.
- SpringDoc/Swagger, split into `public` (JWT-scheme-documented) and `internal` (no auth) groups.
- Flyway `V1__create_leases_schema.sql` + `Lease` entity/repository.
- Shared `ApiResponse`/`ApiError`/`PaginationMeta` envelope, `BusinessException` hierarchy → stable error codes, `GlobalExceptionHandler`, `X-Request-ID` filter/`RequestContext` — the common infrastructure every future endpoint reuses.
- `IdentityServiceClient` — bounded-timeout (2s/5s) REST call to identity-access-service's internal user-validation endpoint; failures surface as `503 DEPENDENCY_UNAVAILABLE`.
- Lease business rules in `LeaseService`: date validation, tenant validation, date-overlap check (Rule 1, on create *and* re-checked on activation), lease-status transition guard.
- The three ✅ endpoints in §3's tables above (24 tests passing, `./mvnw clean verify` green).

## 6. Known gaps / open follow-ups

- **JWT/role authorization is not enforced anywhere in this service.** Endpoints are documented as requiring `MANAGER` (or other roles) in Swagger, but nothing actually checks a token yet — there's no shared JWT filter/library from Group 1 to build against. Don't assume auth is handled; raise it before shipping past internal dev/testing.
- **No `Occupant`/physical-occupancy entity yet** — endpoint group B (§3) is unbuilt. The internal `validate` endpoint is a stand-in, backed by `Lease.tenantId`, and should be repointed at `Occupant` once it exists.
- **Multi-Occupancy Capacity Check (Rule 2)** and the **Maintenance Relocation Protocol (Rule 4)** are not implemented — both depend on `property-unit-service` integration (capacity limits, maintenance-status webhook/event) that hasn't been built.
- **No Testcontainers/Postgres integration suite** — tests run against H2 with Hibernate `ddl-auto`, not the real Flyway-managed schema. Flyway migrations are exercised only when the service actually boots against PostgreSQL (e.g. via Docker Compose).
- **No Postman collection or API Contract Registry entry yet** for this service's endpoints, despite §4 requiring both.

## 7. Repository & workflow conventions

- Repo: [github.com/jtharindudhanushka/lease-occupancy-service](https://github.com/jtharindudhanushka/lease-occupancy-service), service files at repo root (not nested in a subfolder).
- **Git flow going forward:** feature branch → push → open a PR against `main` → review/merge on GitHub. (The first four increments — init, Swagger, Lease APIs, internal occupancy APIs — were fast-forward-merged directly to `main` before this convention was adopted; that history is left as-is rather than rewritten.)
- Conventional-commit-style messages (`feat:`, `fix:`, `docs:`, `chore:`, `test:`, `build:`, `refactor:`), one logical change per commit.
- Every change is verified with `./mvnw clean verify` before committing.
