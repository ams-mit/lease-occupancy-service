# AGENTS.md — Apartment Management System (AMS)

Context file for AI-assisted development on this project. This is a **group project** with four teams building independent microservices behind a shared API Gateway. This document captures the project-wide architecture and, in detail, the service owned by this developer: **`lease-occupancy-service`** (Group 2).

Role of the person driving this repo: **Backend Developer**, responsible for `lease-occupancy-service`. Tasks/implementation instructions will be provided later — this file is context only, no implementation has started yet.

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

**A. Contractual Leases (public, gateway-routed)**

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/leases` | Manager | Validates tenant ID with Group 1; checks schedule conflicts |
| GET | `/api/v1/leases` | Manager | Filterable by active dates and status |
| GET | `/api/v1/leases/{leaseId}` | Manager or Resident | Resident access only if their `userId` appears as occupant/tenant on this lease |
| GET | `/api/v1/leases/units/{unitId}` | Manager or Owner | Chronological lease history for a unit |
| PATCH | `/api/v1/leases/{leaseId}/status` | Manager | Activate / terminate / complete |

**B. Physical Occupancies (public, gateway-routed)**

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/occupancies` | Manager | Registers physical arrival under active lease/ownership |
| GET | `/api/v1/occupancies/units/{unitId}` | All roles | Current occupants of a unit |
| GET | `/api/v1/occupancies/residents/{residentId}` | All roles | Occupancy history for a resident |
| PATCH | `/api/v1/occupancies/{occupancyId}/status` | Manager | Soft-deactivate (→ `INACTIVE`), never hard-delete |

**C. Internal service-to-service (private network, no JWT)**

| Method | Path | Consumer | Purpose |
|---|---|---|---|
| GET | `/api/v1/internal/occupancies/active-billing` | `billing-payment-service` (Group 3) | Active units + billing targets for recurring invoicing |
| GET | `/api/v1/internal/occupancies/validate` | `operations-service` (Group 4) | Check tenant actively resides in unit before facility booking / maintenance request |

**D. System operations (public/actuator)**

| Method | Path | Notes |
|---|---|---|
| GET | `/actuator/health` | App + DB health, used by Docker Compose |
| GET | `/actuator/info` | Deployment metadata for Gateway |

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

No code has been written yet. This file exists purely to capture project + service context ahead of task-by-task implementation work to follow.
