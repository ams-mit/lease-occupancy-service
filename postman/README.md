# lease-occupancy-service Postman Collection

This directory contains the Postman collection for the `lease-occupancy-service`.

## Included Endpoints
The collection covers all implemented endpoints across Sprint 1 and Sprint 2:
* **A. Contractual Leases:** `POST /leases`, `GET /leases`, `PATCH /leases/{leaseId}/status`, `GET /leases/validate` (Operations validation)
* **B. Unit Occupancy:** `GET /units/{unitId}/active-occupancy` (Billing Service active occupancy lookup)
* **C. Internal Service-to-Service:** `GET /internal/occupancies/active-billing`, `GET /internal/occupancies/validate`
* **D. System Operations:** `/actuator/health`, `/actuator/info`

## Setup Instructions

1. **Import the Collection:** Open Postman, click "Import", and select the `lease-occupancy-service.postman_collection.json` file.
2. **Environment Variables:** The collection relies on the following collection variables:
    * `base_url`: Defaults to `http://localhost:8084/api/v1`.
    * `jwt_token`: Leave empty in the collection variables, but you must set this in your active environment for authenticated endpoints to work.

## JWT Authentication Requirements

Every endpoint (except System Operations) requires a Gateway-issued JWT sent in the `Authorization: Bearer <token>` header.

### 1. Public Endpoints (Contractual Leases)
* Require a **User JWT** (`type=user`).
* Must contain the role `MANAGER` in its claims.

### 2. Internal Endpoints (Service-to-Service)
* Require a **Service JWT** (`type=service`).
* The `sub` claim must match the specific allowed caller:
    * `/internal/occupancies/active-billing` requires `sub=billing-payment-service`.
    * `/internal/occupancies/validate` requires `sub=operations-service`.

### Generating Tokens for Local Dev
For local development and testing, you can use the `TestJwtTokens` utility class (or an equivalent script) to generate valid JWTs signed by the test keypair. In production or integrated environments, these tokens must be issued and signed by the actual API Gateway.
