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
3. **Gateway is the only public entry point** for the frontend *and* the only path for internal service-to-service calls — per the org-wide JWT standard (§8), internal calls are **not** unauthenticated; they carry a Gateway-issued Service JWT that the receiving service must verify. (This corrects an earlier version of this doc that said internal endpoints skip JWT entirely — see §8.)
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
| ✅ | POST | `/api/v1/leases` | MANAGER | Validates tenant ID with Group 1; checks schedule conflicts |
| ✅ | GET | `/api/v1/leases` | MANAGER | Filterable by status and an active-on date, paginated |
| ⏳ | GET | `/api/v1/leases/{leaseId}` | MANAGER or RESIDENT | Resident access only if their `userId` appears as occupant/tenant on this lease |
| ⏳ | GET | `/api/v1/leases/units/{unitId}` | MANAGER or OWNER | Chronological lease history for a unit |
| ✅ | PATCH | `/api/v1/leases/{leaseId}/status` | MANAGER | Activate / terminate / complete, with transition + re-overlap checks |

**B. Physical Occupancies (public, gateway-routed)** — ⏳ not started (no `Occupant` entity yet)

| | Method | Path | Auth | Notes |
|---|---|---|---|---|
| ⏳ | POST | `/api/v1/occupancies` | Manager | Registers physical arrival under active lease/ownership |
| ⏳ | GET | `/api/v1/occupancies/units/{unitId}` | All roles | Current occupants of a unit |
| ⏳ | GET | `/api/v1/occupancies/residents/{residentId}` | All roles | Occupancy history for a resident |
| ⏳ | PATCH | `/api/v1/occupancies/{occupancyId}/status` | Manager | Soft-deactivate (→ `INACTIVE`), never hard-delete |

**C. Internal service-to-service (Gateway-routed, Service JWT — see §8; NOT unauthenticated)**

| | Method | Path | Allowed caller (`sub`) | Purpose |
|---|---|---|---|---|
| ✅ | GET | `/api/v1/internal/occupancies/active-billing` | `billing-payment-service` (Group 3) | Active units + billing targets for recurring invoicing |
| ✅ | GET | `/api/v1/internal/occupancies/validate` | `operations-service` (Group 4) | Check tenant actively resides in unit before facility booking / maintenance request. Currently backed by `Lease` (tenant-of-record); should switch to `Occupant` once B exists |

**D. System operations (public/actuator, unauthenticated)**

| | Method | Path | Notes |
|---|---|---|---|
| ✅ | GET | `/actuator/health` | App + DB health, used by Docker Compose |
| ✅ | GET | `/actuator/info` | Deployment metadata for Gateway |
| ✅ | GET | `/swagger-ui.html`, `/v3/api-docs` | SpringDoc, split into `public`/`internal` groups |

Role and service-caller checks (§8) are enforced on every ✅ endpoint above except §D. **Caveat:** until a real `GATEWAY_JWT_PUBLIC_KEY` is configured, this service verifies against a locally-generated ephemeral keypair — it will reject every token actually signed by a real Gateway. See §8.3.

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
- Headers: `Content-Type: application/json`, `Authorization: Bearer <JWT>` (a **User JWT** on public endpoints, a **Service JWT** on internal ones — both Gateway-issued, see §8), `X-Request-ID: <UUID>`.
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
- SpringDoc/Swagger, split into `public` and `internal` groups, both documenting the shared `bearerAuth` (Gateway JWT) requirement.
- Flyway `V1__create_leases_schema.sql` + `Lease` entity/repository.
- Shared `ApiResponse`/`ApiError`/`PaginationMeta` envelope, `BusinessException` hierarchy → stable error codes, `GlobalExceptionHandler`, `X-Request-ID` filter/`RequestContext` — the common infrastructure every future endpoint reuses.
- **JWT authentication & authorization (§8), fully implemented:** `JwtKeyConfig` (RSA key loading, with an ephemeral-dev-key fallback), `JwtService` (verify Gateway JWTs / mint outbound Service JWTs), `JwtAuthenticationFilter` (runs on every request except `/actuator`, `/v3/api-docs`, `/swagger-ui`), `AuthContext` (role/service-caller checks called explicitly at the top of each controller method — no Spring Security, no annotation magic). `401 UNAUTHENTICATED` / `403 PERMISSION_DENIED` flow through the existing envelope.
- `IdentityServiceClient` — now routes through the Gateway (`GATEWAY_URL`, was direct to identity-access-service) carrying a self-minted Service JWT; bounded-timeout (2s/5s); failures surface as `503 DEPENDENCY_UNAVAILABLE`.
- Lease business rules in `LeaseService`: date validation, tenant validation, date-overlap check (Rule 1, on create *and* re-checked on activation), lease-status transition guard.
- The five ✅ endpoints in §3's tables above, all JWT-protected (31 tests passing, `./mvnw clean verify` green).

## 6. Known gaps / open follow-ups

- **No real Gateway public key yet.** JWT verification (§8) is fully implemented but has never been tested against an actual Gateway — there isn't one in this repo set. Until `GATEWAY_JWT_PUBLIC_KEY` is set to a real value, this service silently generates and verifies against its own ephemeral keypair, which means it currently accepts *nothing* signed by a real Gateway and *only* tokens minted by `TestJwtTokens` in tests. Revisit as soon as the Gateway team publishes a real key.
- **No `Occupant`/physical-occupancy entity yet** — endpoint group B (§3) is unbuilt. The internal `validate` endpoint is a stand-in, backed by `Lease.tenantId`, and should be repointed at `Occupant` once it exists.
- **Multi-Occupancy Capacity Check (Rule 2)** and the **Maintenance Relocation Protocol (Rule 4)** are not implemented — both depend on `property-unit-service` integration (capacity limits, maintenance-status webhook/event) that hasn't been built.
- **No Testcontainers/Postgres integration suite** — tests run against H2 with Hibernate `ddl-auto`, not the real Flyway-managed schema. Flyway migrations are exercised only when the service actually boots against PostgreSQL (e.g. via Docker Compose).
- **No Postman collection or API Contract Registry entry yet** for this service's endpoints, despite §4 requiring both — now also needs to cover the `Authorization: Bearer <JWT>` requirement on every example.
- **Role list is provisional.** `MANAGER`/`RESIDENT`/`OWNER` come from this doc's own endpoint contract (§3), not a shared, ratified list of role names from Group 1 — confirm exact spelling/casing once Identity Access publishes one, since `AuthContext.requireRole` does an exact string match.

## 7. Repository & workflow conventions

- Repo: [github.com/ams-mit/lease-occupancy-service](https://github.com/ams-mit/lease-occupancy-service) (moved from a personal account into the `ams-mit` org), service files at repo root (not nested in a subfolder).
- **Git flow going forward:** feature branch → push → open a PR against `main` → review/merge on GitHub. (The first four increments — init, Swagger, Lease APIs, internal occupancy APIs — were fast-forward-merged directly to `main` before this convention was adopted; that history is left as-is rather than rewritten.)
- Conventional-commit-style messages (`feat:`, `fix:`, `docs:`, `chore:`, `test:`, `build:`, `refactor:`), one logical change per commit.
- Every change is verified with `./mvnw clean verify` before committing.

---

## 8. JWT Authentication & Security Standard (org-wide, ratified)

**Status: ✅ implemented in this service**, except the one external dependency nothing here can fix on its own — a real Gateway public key (§6).

Full spec: [Project_A_JWT_Authentication_and_Security_Standard.md](Project_A_JWT_Authentication_and_Security_Standard.md) — **read it in full before changing anything below.** This section is a lease-occupancy-service-specific summary + implementation record, not a replacement for the source doc.

### 8.1 The model, in one paragraph

Everything goes through the Gateway — including internal service-to-service calls. A caller (frontend user or another backend service) authenticates once upstream (Identity Access for users; each service signs its own token for itself), the Gateway verifies that token and **re-signs a brand-new, short-lived JWT** with the Gateway's own private key, and only that Gateway-signed JWT ever reaches us. We never see or trust a User JWT signed by Identity Access or a Service JWT signed by another service directly — only Gateway JWTs, verified with the **Gateway's public key**. Two token shapes, both Gateway-issued: `type=user` (has `roles`) and `type=service` (`sub` = calling service name).

### 8.2 What this corrects from earlier in this doc

- §3C's "no JWT" on internal endpoints was **wrong** — internal calls require a Gateway-issued Service JWT, verified the same way as user calls, just checking `sub`/allowed-caller instead of `roles`.
- `IdentityServiceClient` calling identity-access-service **directly** is wrong — it must go through the Gateway and carry our own signed Service JWT.

### 8.3 Implementation checklist for lease-occupancy-service

**Keys & config**
- [x] RSA keypair handling for `lease-occupancy-service` — `JwtKeyConfig` reads `SERVICE_JWT_PRIVATE_KEY` (PEM/PKCS8); falls back to an ephemeral generated keypair with a loud warning if unset. Public key still needs to be **registered with whoever owns the Gateway** once we have one (manual step, not code).
- [ ] Obtain the real **Gateway's public key** (`GATEWAY_JWT_PUBLIC_KEY`) — blocked on the Gateway team; tracked in §6, not fixable from this repo.
- [x] Env vars: `SERVICE_NAME`, `GATEWAY_JWT_PUBLIC_KEY`, `SERVICE_JWT_PRIVATE_KEY`, `SERVICE_JWT_EXPIRES_IN_SECONDS`, `GATEWAY_URL` — see `.env.example`. (Algorithm is hardcoded RS256 rather than a separate `JWT_ALGORITHM` var, since jjwt's API ties the signer to a concrete algorithm anyway.)
- [x] Private keys: env var only. `*.pem`/`*.key`/`.env` added to `.gitignore` defensively. `.env.example` has placeholders + `openssl` commands to generate a real keypair.

**Dependency**
- [x] `io.jsonwebtoken:jjwt-api`/`jjwt-impl`/`jjwt-jackson` 0.12.6.

**Inbound verification (`JwtAuthenticationFilter`, ordered right after `RequestIdFilter`)**
- [x] Missing/malformed `Authorization` header → `401 UNAUTHENTICATED`.
- [x] Signature verified against the Gateway public key (RS256); any `JwtException` (including expiry, since jjwt checks `exp` during parsing) → `401 UNAUTHENTICATED`.
- [x] Branches on `type`:
  - `user` → `AuthContext.requireRole(role)`, called explicitly at the top of each `LeaseController` method → `403 PERMISSION_DENIED` if the role doesn't match.
  - `service` → `AuthContext.requireServiceCaller(...)`, called explicitly at the top of each `InternalOccupancyController` method (`operations-service` for `/validate`, `billing-payment-service` for `/active-billing`) → `403 PERMISSION_DENIED` if not allowed.
- [x] Unrecognized `type`, or the wrong token shape for an endpoint → rejected (`InvalidTokenException`/`ForbiddenException`).
- [x] `401`/`403` flow through the existing `ApiResponse`/`ApiError` envelope — the filter hands exceptions to Spring's `HandlerExceptionResolver` so `GlobalExceptionHandler` handles them exactly like a controller-thrown exception (a filter's own exceptions otherwise bypass `@RestControllerAdvice` entirely). No Spring Security in this service — authorization is explicit `AuthContext` calls, not annotations.
- `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**` are exempted (`shouldNotFilter`) — infra/tooling endpoints stay open.

**Outbound (`IdentityServiceClient`)**
- [x] Mints a Service JWT (`sub=lease-occupancy-service`, `type=service`, configurable TTL) via `JwtService.mintServiceToken()` before every call.
- [x] Calls the Gateway's base URL (`gatewayRestClient`, `GATEWAY_URL`) rather than identity-access-service directly, assuming the Gateway proxies `/api/v1/internal/users/**` at the same path — **unverified assumption**, adjust once real Gateway routing conventions are published.

**Docs**
- [x] §3C's endpoint table and `OpenApiConfig`'s security requirement (now declared globally, both groups) updated to reflect the Service JWT requirement.
- [ ] API Contract Registry entry — still doesn't exist for this service at all (§6).

**Tests**
- [x] `TestJwtTokens` (test-only) mints tokens signed with a fixed test RSA keypair matching `app.jwt.gateway-public-key` in `src/test/resources/application.yml`, so `@WebMvcTest` slices can exercise real JWT verification without a live Gateway.
- [x] Coverage: missing token, expired token, wrong role, wrong calling service, right role/service — across both `LeaseControllerTest` and `InternalOccupancyControllerTest`.

### 8.4 Explicitly out of scope for this service

- We do **not** implement the Gateway itself, or Identity Access's login/user-JWT issuance — those are owned by whoever builds the Gateway and Group 1 respectively.
- We do **not** need any other backend service's public key — only the Gateway's (§10–11 of the source doc: services trust the Gateway, not each other).
