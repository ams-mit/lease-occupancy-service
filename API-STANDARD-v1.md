# Apartment Management System — API Standard v1

**Project:** Apartment Management System  
**Document:** Shared API Standard  
**Version:** 1.0  
**Status:** Proposed / Team Agreement  
**Applies to:** All project microservices and API Gateway

---

## 1. Purpose

This document defines the common API conventions for the Apartment Management System.

All teams should follow these conventions so that:

- the React frontend can communicate with all services consistently;
- microservices have predictable request and response formats;
- errors can be handled consistently;
- JWT authentication and authorization are applied uniformly;
- cross-service communication is clear and traceable;
- OpenAPI/Swagger documentation follows one structure;
- breaking API changes are controlled and communicated.

The project requires REST APIs, JWT-based security, API Gateway integration, OpenAPI/Swagger documentation, Postman testing, and documented cross-team contracts.

---

## 2. Technology Standard

| Area | Standard |
|---|---|
| Protocol | HTTP/HTTPS |
| API Style | REST |
| Data Format | JSON |
| Backend | Spring Boot |
| API Gateway | Central API Gateway |
| Authentication | JWT Bearer Token |
| API Documentation | OpenAPI / Swagger |
| API Testing | Postman + automated tests |
| Database | MySQL |
| API Versioning | URI versioning |
| Character Encoding | UTF-8 |
| Date/Time | ISO-8601 |
| API IDs | UUID |
| JSON Naming | camelCase |
| URL Naming | kebab-case |
| Resource Naming | Plural nouns |

---

## 3. API Gateway

The frontend should communicate through the API Gateway rather than directly calling individual microservices.

```text
React Frontend
      |
      v
+------------------+
|   API Gateway    |
+------------------+
      |
      +---------------------------+
      |            |              |
      v            v              v
  Identity     Property        Billing
  Services     Services        Services
      |
      +---------------------------+
      |            |              |
      v            v              v
 Resident       Utility       Operations
 Services       Services      Services
                                   |
                                   v
                              Community
                               Services
```

### Gateway responsibilities

The API Gateway should provide the common entry point for:

- routing;
- authentication integration;
- authorization-related checks where appropriate;
- request ID propagation;
- centralized API access;
- consistent handling of downstream failures.

The Gateway, shared frontend shell, Docker Compose integration, and common release documentation are jointly governed by the project integration process.

---

## 4. Service Ownership

The project contains the following service ownership areas:

### Group 1 — Identity and Resident Management

```text
identity-access-service
resident-management-service
```

### Group 2 — Property and Lease Management

```text
property-unit-service
lease-occupancy-service
```

### Group 3 — Billing and Utilities

```text
billing-payment-service
utility-charge-service
```

### Group 4 — Operations and Community

```text
operations-service
community-service
```

Each service owns its own data. Other services must access that data through documented APIs rather than directly accessing another service's database.

---

# 5. Base URL and Versioning

All APIs should use:

```text
/api/v1
```

Example:

```http
GET /api/v1/residents
```

```http
GET /api/v1/residents/{residentId}
```

```http
POST /api/v1/residents
```

## Versioning rule

Use:

```text
/api/v1/...
```

for the first stable API contract.

A breaking API change should use a new major API version:

```text
/api/v2/...
```

Do not silently introduce breaking changes into `/api/v1`.

API contract changes must be communicated to dependent teams before implementation.

---

# 6. URL Naming Convention

Use REST resource names rather than action-based URLs.

### Recommended

```http
GET    /api/v1/residents
GET    /api/v1/residents/{residentId}
POST   /api/v1/residents
PUT    /api/v1/residents/{residentId}
PATCH  /api/v1/residents/{residentId}
DELETE /api/v1/residents/{residentId}
```

### Avoid

```http
GET  /api/v1/getResidents
POST /api/v1/createResident
POST /api/v1/resident/create
GET  /api/v1/getAllResidents
```

Use plural nouns for collections.

---

# 7. HTTP Methods

| Method | Purpose |
|---|---|
| `GET` | Retrieve resource(s) |
| `POST` | Create a resource or perform a non-idempotent operation |
| `PUT` | Replace/update a resource |
| `PATCH` | Partially update a resource |
| `DELETE` | Delete/deactivate a resource where appropriate |

### Example

```http
POST /api/v1/residents
```

creates a resident.

```http
GET /api/v1/residents/{residentId}
```

retrieves a resident.

```http
PATCH /api/v1/residents/{residentId}/status
```

changes a resident's status.

---

# 8. Standard Request Headers

Requests should use:

```http
Content-Type: application/json
Accept: application/json
Authorization: Bearer <JWT>
X-Request-ID: <UUID>
```

### `X-Request-ID`

The request ID should be propagated through the Gateway and downstream services.

Example:

```text
Frontend
   |
   | X-Request-ID: 7f83a9b2-...
   v
API Gateway
   |
   | X-Request-ID: 7f83a9b2-...
   v
Resident Service
   |
   | X-Request-ID: 7f83a9b2-...
   v
Application Logs
```

This allows a request to be traced across multiple services.

---

# 9. Authentication

Protected APIs use JWT Bearer authentication.

```http
Authorization: Bearer <JWT>
```

Example:

```http
GET /api/v1/residents/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

The authentication service is responsible for issuing JWTs.

Services must validate authentication before allowing protected operations.

---

# 10. Authorization

Authentication and authorization are different.

```text
Authentication
      |
      v
Is the JWT valid?
      |
      v
Is the account active?
      |
      v
Does the user have the required role?
      |
      v
Does the user have the required relationship/scope?
      |
      v
Allow / Deny
```

Authorization should not rely only on whether a user is logged in.

The project requires role-based access and verification of relevant apartment/unit relationships.

---

# 11. Standard Success Response

All services should use a common response envelope.

## Single Resource

```json
{
  "success": true,
  "message": "Resident retrieved successfully",
  "data": {
    "id": "8d3f5c7e-7c3a-4f4c-a7b1-1c7d6e9a1234",
    "userId": "3a1e2b4c-1234-4567-8901-abcdef123456",
    "firstName": "John",
    "lastName": "Perera",
    "email": "john@example.com",
    "phone": "0771234567",
    "status": "ACTIVE",
    "createdAt": "2026-08-28T10:30:00Z",
    "updatedAt": "2026-08-28T11:15:00Z"
  },
  "timestamp": "2026-08-28T11:20:00Z",
  "requestId": "req-7f83a9b2"
}
```

### Required fields

| Field | Description |
|---|---|
| `success` | Indicates whether the operation succeeded |
| `message` | Human-readable result message |
| `data` | Returned resource/data |
| `timestamp` | Server response timestamp |
| `requestId` | ID used to trace the request |

---

# 12. Standard List Response

Collection endpoints should support pagination.

```json
{
  "success": true,
  "message": "Residents retrieved successfully",
  "data": [
    {
      "id": "uuid-1",
      "firstName": "John",
      "lastName": "Perera",
      "status": "ACTIVE"
    },
    {
      "id": "uuid-2",
      "firstName": "Sarah",
      "lastName": "Fernando",
      "status": "ACTIVE"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": "2026-08-28T11:20:00Z",
  "requestId": "req-7f83a9b2"
}
```

---

# 13. Pagination

Default pagination:

```text
page = 0
size = 20
```

Maximum recommended page size:

```text
size = 100
```

Example:

```http
GET /api/v1/residents?page=0&size=20
```

### Pagination fields

```json
{
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "hasNext": true,
  "hasPrevious": false
}
```

---

# 14. Searching, Filtering and Sorting

Use query parameters.

### Filtering

```http
GET /api/v1/residents?status=ACTIVE
```

### Multiple filters

```http
GET /api/v1/maintenance-requests?status=OPEN&priority=HIGH
```

### Search

```http
GET /api/v1/residents?search=perera
```

### Sorting

```http
GET /api/v1/residents?sort=lastName,asc
```

### Combined

```http
GET /api/v1/residents?search=perera&status=ACTIVE&page=0&size=20&sort=lastName,asc
```

---

# 15. Standard Error Response

All services must use the same basic error structure.

```json
{
  "success": false,
  "message": "Resident not found",
  "error": {
    "code": "RESIDENT_NOT_FOUND",
    "details": null
  },
  "timestamp": "2026-08-28T11:25:00Z",
  "requestId": "req-7f83a9b2"
}
```

### Error fields

| Field | Description |
|---|---|
| `success` | Always `false` |
| `message` | Human-readable explanation |
| `error.code` | Machine-readable error code |
| `error.details` | Optional structured details |
| `timestamp` | Server response timestamp |
| `requestId` | Request trace identifier |

---

# 16. Validation Error Response

Validation failures should provide field-level details.

```json
{
  "success": false,
  "message": "Validation failed",
  "error": {
    "code": "VALIDATION_ERROR",
    "details": [
      {
        "field": "email",
        "message": "Email must be valid"
      },
      {
        "field": "phone",
        "message": "Phone number is required"
      }
    ]
  },
  "timestamp": "2026-08-28T11:25:00Z",
  "requestId": "req-abc123"
}
```

---

# 17. HTTP Status Code Standard

| Status | Meaning | Typical Usage |
|---|---|---|
| `200 OK` | Successful request | GET, PUT, PATCH |
| `201 Created` | Resource created | POST |
| `202 Accepted` | Request accepted for later processing | Optional asynchronous operation |
| `204 No Content` | Successful operation without response body | DELETE |
| `400 Bad Request` | Invalid request | Malformed request |
| `401 Unauthorized` | Authentication failed/missing | Invalid or missing JWT |
| `403 Forbidden` | Authenticated but not authorized | Insufficient permission |
| `404 Not Found` | Resource does not exist | Unknown ID |
| `409 Conflict` | Business/state conflict | Duplicate or conflicting operation |
| `422 Unprocessable Entity` | Semantically invalid data | Optional project-wide usage |
| `429 Too Many Requests` | Rate limit exceeded | If rate limiting is implemented |
| `500 Internal Server Error` | Unexpected server error | Unhandled server failure |
| `503 Service Unavailable` | Dependency/service unavailable | Downstream service failure |

---

# 18. Business Error Codes

Error codes must be stable and machine-readable.

## Identity

```text
USER_NOT_FOUND
USER_ALREADY_EXISTS
INVALID_CREDENTIALS
ACCOUNT_INACTIVE
ROLE_NOT_FOUND
PERMISSION_DENIED
```

## Resident

```text
RESIDENT_NOT_FOUND
RESIDENT_ALREADY_EXISTS
INVALID_UNIT_RELATIONSHIP
```

## Property

```text
UNIT_NOT_FOUND
UNIT_ALREADY_OCCUPIED
UNIT_UNAVAILABLE
```

## Lease/Occupancy

```text
LEASE_NOT_FOUND
OCCUPANCY_CONFLICT
INVALID_LEASE_STATUS
```

## Billing

```text
INVOICE_NOT_FOUND
PAYMENT_EXCEEDS_BALANCE
INVALID_PAYMENT_STATUS
```

## Operations

```text
MAINTENANCE_REQUEST_NOT_FOUND
INVALID_REQUEST_STATUS
UNAUTHORIZED_STATUS_CHANGE
```

## General

```text
VALIDATION_ERROR
RESOURCE_NOT_FOUND
DUPLICATE_RESOURCE
BUSINESS_RULE_VIOLATION
DEPENDENCY_UNAVAILABLE
INTERNAL_SERVER_ERROR
```

Frontend code should use error codes rather than matching human-readable messages.

### Good

```javascript
if (error.code === "RESIDENT_NOT_FOUND") {
    // show not-found UI
}
```

### Avoid

```javascript
if (message === "Resident not found") {
    // ...
}
```

---

# 19. UUID Standard

API-facing entity identifiers should use UUIDs.

Example:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000"
}
```

Use consistent UUID representation across services.

---

# 20. Date and Time Standard

Use ISO-8601.

### Date and time

```json
{
  "createdAt": "2026-08-28T14:30:00Z"
}
```

### Date only

```json
{
  "dateOfBirth": "2002-05-15"
}
```

### Time only

```json
{
  "startTime": "14:30:00"
}
```

Do not use inconsistent formats such as:

```text
28/08/2026
08-28-2026
28-08-2026 14:30
```

---

# 21. Enum Standard

Use uppercase enum values.

Example:

```json
{
  "status": "ACTIVE"
}
```

Recommended general values include:

```text
ACTIVE
INACTIVE
PENDING
APPROVED
REJECTED
CANCELLED
COMPLETED
```

Each domain should define its own valid state transitions.

---

# 22. POST Standard

Use POST to create resources.

```http
POST /api/v1/residents
```

Request:

```json
{
  "userId": "uuid",
  "firstName": "John",
  "lastName": "Perera",
  "email": "john@example.com",
  "phone": "0771234567"
}
```

Success:

```http
201 Created
```

---

# 23. PUT Standard

Use PUT for full resource replacement/update.

```http
PUT /api/v1/residents/{residentId}
```

The request should contain the complete representation expected by the endpoint.

---

# 24. PATCH Standard

Use PATCH for partial updates.

```http
PATCH /api/v1/residents/{residentId}
```

Example:

```json
{
  "phone": "0771234567"
}
```

For controlled state changes, a dedicated sub-resource/action endpoint may be used where it makes the business operation clearer:

```http
PATCH /api/v1/residents/{residentId}/status
```

```json
{
  "status": "INACTIVE",
  "reason": "Resident moved out"
}
```

---

# 25. DELETE Standard

Use DELETE where actual deletion is appropriate.

```http
DELETE /api/v1/resource/{id}
```

Expected response:

```http
204 No Content
```

For important business records, prefer a domain-specific status/deactivation operation when historical/audit information must be preserved.

---

# 26. Inter-Service Communication

Services must communicate through documented APIs.

### Correct

```text
Service A
   |
   | REST API
   v
Service B
   |
   v
Service B Database
```

### Not allowed

```text
Service A
   |
   | Direct database access
   v
Service B Database
```

A service must not directly access another service's database.

Each service remains responsible for its own data.

---

# 27. Internal API Example

A service that needs to validate a user can call an identity/resident API.

```http
GET /api/v1/users/{userId}/validation
```

Example response:

```json
{
  "success": true,
  "message": "User validation successful",
  "data": {
    "userId": "uuid",
    "exists": true,
    "active": true,
    "roles": [
      "TENANT"
    ],
    "unitRelationships": [
      {
        "unitId": "unit-uuid",
        "relationship": "TENANT",
        "active": true
      }
    ]
  },
  "timestamp": "2026-08-28T12:00:00Z",
  "requestId": "abc-123"
}
```

---

# 28. Dependency Failure

If a downstream service is unavailable, do not return a successful response containing misleading empty data.

### Incorrect

```json
{
  "success": true,
  "data": {}
}
```

### Correct

```http
503 Service Unavailable
```

```json
{
  "success": false,
  "message": "Identity service is temporarily unavailable",
  "error": {
    "code": "DEPENDENCY_UNAVAILABLE",
    "details": {
      "service": "identity-access-service"
    }
  },
  "timestamp": "2026-08-28T12:05:00Z",
  "requestId": "abc-123"
}
```

A failed dependency must not cause local records to be incorrectly created, updated, or deleted.

---

# 29. Inter-Service Timeout

The project should agree on a bounded timeout for synchronous service calls.

Recommended starting point:

```text
Connection timeout: 2 seconds
Read timeout:       5 seconds
```

Services must not wait indefinitely for another service.

If the dependency cannot respond within the configured limit, return an appropriate dependency failure such as:

```http
503 Service Unavailable
```

The actual values should be documented in the deployment/configuration documentation if changed.

---

# 30. Health Checks

Every service should expose a health endpoint.

Recommended Spring Boot endpoint:

```http
GET /actuator/health
```

Example:

```text
identity-access-service
resident-management-service
property-unit-service
lease-occupancy-service
billing-payment-service
utility-charge-service
operations-service
community-service
```

Health checks should be usable by Docker Compose/deployment tooling and for troubleshooting.

---

# 31. Logging and Traceability

Important operations should be logged.

Examples:

```text
LOGIN_SUCCESS
LOGIN_FAILURE
USER_CREATED
ROLE_CHANGED
RESIDENT_CREATED
RESIDENT_DEACTIVATED
LEASE_APPROVED
PAYMENT_RECORDED
MAINTENANCE_REQUEST_CREATED
WORK_ORDER_ASSIGNED
WORK_ORDER_COMPLETED
BOOKING_CREATED
ANNOUNCEMENT_PUBLISHED
```

Logs should include, where appropriate:

```text
timestamp
service
requestId
userId
operation
result
errorCode
```

Do not log:

```text
passwords
JWT secrets
sensitive credentials
```

---

# 32. Security Rules

All services must follow the project's security requirements.

### Passwords

Never store plain-text passwords.

### JWT

Never expose JWT secrets.

### Validation

Validate request payloads before processing.

### Authorization

Check both role and required business relationship/scope.

### Errors

Do not expose stack traces or sensitive implementation details to clients.

### Example safe error

```json
{
  "success": false,
  "message": "Unable to process request",
  "error": {
    "code": "INTERNAL_SERVER_ERROR",
    "details": null
  },
  "timestamp": "2026-08-28T12:00:00Z",
  "requestId": "abc-123"
}
```

---

# 33. Spring Boot Validation

Use DTO validation.

Example:

```java
@NotBlank
private String firstName;

@NotBlank
private String lastName;

@NotBlank
@Email
private String email;
```

Validation failures must use the standard:

```text
VALIDATION_ERROR
```

response structure.

---

# 34. Global Exception Handling

Each Spring Boot service should implement centralized exception handling.

Recommended approach:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // Handle validation errors
    // Handle resource-not-found errors
    // Handle business-rule errors
    // Handle authorization errors
    // Handle unexpected exceptions
}
```

The purpose is to ensure that every controller does not create its own incompatible error response.

---

# 35. API Documentation

Every endpoint must be documented using OpenAPI/Swagger.

Documentation should include:

- endpoint;
- HTTP method;
- description;
- authentication requirement;
- required roles;
- path parameters;
- query parameters;
- request body;
- validation rules;
- successful response;
- error responses;
- business error codes;
- dependent services;
- failure behavior;
- examples.

Example:

```text
API ID:
RES-001

Service:
resident-management-service

Version:
v1

Method:
POST

Endpoint:
/api/v1/residents

Purpose:
Create a resident.

Authentication:
Bearer JWT

Required Roles:
APARTMENT_MANAGER
SYSTEM_ADMIN

Success:
201 Created

Errors:
400 VALIDATION_ERROR
401 INVALID_CREDENTIALS
403 PERMISSION_DENIED
409 RESIDENT_ALREADY_EXISTS
503 DEPENDENCY_UNAVAILABLE

Dependencies:
identity-access-service
```

---

# 36. API Contract Registry

The project should maintain a central API contract registry.

Recommended structure:

```text
docs/
└── api/
    ├── API-STANDARD-v1.md
    ├── API-CONTRACT-REGISTRY.md
    └── openapi/
        ├── identity-access.yaml
        ├── resident-management.yaml
        ├── property-unit.yaml
        ├── lease-occupancy.yaml
        ├── billing-payment.yaml
        ├── utility-charge.yaml
        ├── operations.yaml
        └── community.yaml
```

The registry should record:

| API ID | Service | Method | Endpoint | Owner | Version | Status |
|---|---|---|---|---|---|---|
| AUTH-001 | identity-access | POST | `/api/v1/auth/login` | Group 1 | v1 | Active |
| RES-001 | resident-management | POST | `/api/v1/residents` | Group 1 | v1 | Active |
| PROP-001 | property-unit | GET | `/api/v1/units` | Group 2 | v1 | Active |
| LEASE-001 | lease-occupancy | GET | `/api/v1/leases` | Group 2 | v1 | Active |
| BILL-001 | billing-payment | GET | `/api/v1/invoices` | Group 3 | v1 | Active |
| UTIL-001 | utility-charge | GET | `/api/v1/utilities` | Group 3 | v1 | Active |
| OPS-001 | operations | POST | `/api/v1/maintenance-requests` | Group 4 | v1 | Active |
| COMM-001 | community | GET | `/api/v1/announcements` | Group 4 | v1 | Active |

Teams should extend this registry as endpoints are implemented.

---

# 37. API Change Management

Before changing a shared API contract:

1. Identify affected services.
2. Update the API contract.
3. Notify dependent teams.
4. Review the change with the project integration process.
5. Update OpenAPI documentation.
6. Update Postman collections.
7. Update automated tests.
8. Update frontend consumers if necessary.
9. Record the change in the contract registry.

### Breaking changes

Examples:

```text
Removing a response field
Renaming a field
Changing field type
Changing required/optional behavior
Changing an existing endpoint's meaning
Changing authentication requirements
```

Do not introduce these silently.

---

# 38. Example — Login API

## Request

```http
POST /api/v1/auth/login
```

```json
{
  "username": "john@example.com",
  "password": "Password123!"
}
```

## Success

```http
200 OK
```

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": "uuid",
      "username": "john@example.com",
      "roles": [
        "TENANT"
      ]
    }
  },
  "timestamp": "2026-08-28T12:00:00Z",
  "requestId": "abc-123"
}
```

## Invalid credentials

```http
401 Unauthorized
```

```json
{
  "success": false,
  "message": "Invalid username or password",
  "error": {
    "code": "INVALID_CREDENTIALS",
    "details": null
  },
  "timestamp": "2026-08-28T12:00:00Z",
  "requestId": "abc-123"
}
```

---

# 39. Example — Resident API

## Create Resident

```http
POST /api/v1/residents
```

Request:

```json
{
  "userId": "uuid",
  "firstName": "John",
  "lastName": "Perera",
  "email": "john@example.com",
  "phone": "0771234567"
}
```

Response:

```http
201 Created
```

```json
{
  "success": true,
  "message": "Resident created successfully",
  "data": {
    "id": "uuid",
    "userId": "uuid",
    "firstName": "John",
    "lastName": "Perera",
    "email": "john@example.com",
    "phone": "0771234567",
    "status": "ACTIVE"
  },
  "timestamp": "2026-08-28T12:00:00Z",
  "requestId": "abc-123"
}
```

---

# 40. Example — Cross-Service Workflow

Example: maintenance request.

```text
Frontend
   |
   v
API Gateway
   |
   v
Operations Service
   |
   | Validate resident
   v
Identity / Resident API
   |
   | Validate unit/property
   v
Property / Unit API
   |
   | Valid
   v
Maintenance Request Created
   |
   v
Notification / Community Service
```

The same principle applies to other cross-team workflows such as:

- resident onboarding;
- maintenance requests;
- facility bookings;
- visitor handling;
- billing/payment workflows;
- community announcements.

---

# 41. API Design Checklist

Before marking an endpoint complete, verify:

### URL

- [ ] Uses `/api/v1`
- [ ] Uses plural resource names
- [ ] Uses kebab-case
- [ ] Does not use unnecessary action names

### Request

- [ ] Correct HTTP method
- [ ] JSON request body where required
- [ ] Validation implemented
- [ ] Authentication requirement documented
- [ ] Authorization requirement documented

### Response

- [ ] Standard success envelope
- [ ] Standard error envelope
- [ ] Correct HTTP status code
- [ ] Stable error code
- [ ] Request ID included
- [ ] Timestamp included

### Security

- [ ] JWT checked
- [ ] Role checked
- [ ] Business relationship/scope checked
- [ ] Sensitive data protected
- [ ] Safe error messages

### Integration

- [ ] Dependencies documented
- [ ] No direct database access to another service
- [ ] Timeout defined
- [ ] Dependency failure handled
- [ ] Request ID propagated

### Documentation

- [ ] OpenAPI updated
- [ ] Swagger verified
- [ ] Postman request added
- [ ] Automated tests added
- [ ] Contract registry updated

---

# 42. Final Shared Standard

The project-wide API contract can be summarized as:

```text
================================================
       APARTMENT MANAGEMENT SYSTEM
              API STANDARD v1
================================================

Base URL:
    /api/v1

Format:
    JSON

Authentication:
    JWT Bearer

Request ID:
    X-Request-ID

ID:
    UUID

Date/Time:
    ISO-8601

JSON Naming:
    camelCase

URL Naming:
    kebab-case

Resource Naming:
    plural nouns

------------------------------------------------
SUCCESS
------------------------------------------------

{
  "success": true,
  "message": "...",
  "data": {},
  "pagination": {},
  "timestamp": "...",
  "requestId": "..."
}

------------------------------------------------
ERROR
------------------------------------------------

{
  "success": false,
  "message": "...",
  "error": {
    "code": "...",
    "details": null
  },
  "timestamp": "...",
  "requestId": "..."
}

------------------------------------------------
HTTP STATUS
------------------------------------------------

200 OK
201 Created
202 Accepted
204 No Content
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
422 Unprocessable Entity
429 Too Many Requests
500 Internal Server Error
503 Service Unavailable

------------------------------------------------
INTER-SERVICE
------------------------------------------------

REST API only
No direct cross-service database access
Bounded timeout
Dependency errors -> 503
Request ID propagation
JWT propagation where appropriate

------------------------------------------------
VERSIONING
------------------------------------------------

/api/v1/...

Breaking change:
    /api/v2/...

------------------------------------------------
DOCUMENTATION
------------------------------------------------

OpenAPI / Swagger
Postman Collection
Request examples
Response examples
Error examples
Status codes
Dependencies
Failure behavior
Change history
================================================
```

---

## 43. Governance

This document should be treated as a **shared project contract**.

Changes affecting multiple teams should be discussed and agreed through the project's integration coordination process.

Each service team remains responsible for:

- its own API implementation;
- its own OpenAPI specification;
- its own database;
- automated tests;
- Postman examples;
- service documentation;
- communicating contract changes.

The shared standard should be kept stable throughout development unless a justified project-level change is approved.

---

## 44. Document History

| Version | Date | Change | Author |
|---|---|---|---|
| 1.0 | 2026-08-28 | Initial shared API standard | Project Team |

