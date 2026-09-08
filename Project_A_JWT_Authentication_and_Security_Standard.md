# Project A — JWT Authentication & Service-to-Service Security Standard

## 1. Purpose

This document defines the JWT standard for Project A (Apartment Management System), including user authentication, API Gateway authentication, backend JWT verification, service-to-service authentication, Gateway-issued internal JWTs, JWT claims, key ownership and trust, environment variables, validation, authorization, error handling, and security rules.

Where the assignment documents do not prescribe an exact JWT implementation detail, the choices below are the project's design decisions.

## 2. Authentication Architecture

There are two request types:

1. **User request:** Frontend → API Gateway → Backend Service
2. **Internal service request:** Service A → API Gateway → Service B

The API Gateway is the central trust point for backend services.

The architecture uses JWT, RS256 asymmetric signing, separate user/service authentication, Gateway-issued JWTs for backend requests, HTTPS, role-based authorization for users, and service-level authorization for internal calls.

## 3. JWT Types

Every JWT has a `type` claim.

### User JWT

```json
{
  "sub": "user_123",
  "type": "user",
  "roles": ["TENANT"],
  "iat": 1750000000,
  "exp": 1750001800
}
```

`type = user` means the token represents an authenticated user.

### Service JWT

```json
{
  "sub": "resident-management-service",
  "type": "service",
  "iat": 1750000000,
  "exp": 1750000300
}
```

`type = service` means the token represents an authenticated internal service.

## 4. Signing Algorithm

The project uses:

```text
RS256
```

The private key signs the JWT. The corresponding public key verifies it.

## 5. JWT Header

```json
{
  "alg": "RS256",
  "typ": "JWT"
}
```

## 6. JWT Claims

- `sub`: subject. For a user, the user ID. For a service, the service name.
- `type`: `user` or `service`.
- `roles`: user roles; used for user authorization. Not required for service JWTs.
- `iat`: Unix timestamp when issued.
- `exp`: Unix timestamp when the token expires.

## 7. Exact Gateway JWT Format

The API Gateway creates a new JWT after successfully validating the incoming User JWT or Service JWT.

### Gateway User JWT

Header:

```json
{
  "alg": "RS256",
  "typ": "JWT"
}
```

Payload:

```json
{
  "sub": "user_123",
  "type": "user",
  "roles": ["TENANT"],
  "iat": 1750000000,
  "exp": 1750000300
}
```

The Gateway User JWT preserves only the trusted user identity and role information required by backend services. It is signed with the Gateway private key and has a short lifetime of approximately 5 minutes.

### Gateway Service JWT

Header:

```json
{
  "alg": "RS256",
  "typ": "JWT"
}
```

Payload:

```json
{
  "sub": "resident-management-service",
  "type": "service",
  "iat": 1750000000,
  "exp": 1750000300
}
```

The Gateway Service JWT identifies the authenticated calling service. It is signed with the Gateway private key and has a short lifetime of approximately 5 minutes.

The original User JWT or Service JWT is never forwarded to the backend service.

## 8. Claims Not Included

The standard deliberately does not include:

- Passwords or password hashes
- Secrets
- Sensitive profile data
- Apartment/unit ownership data
- Occupancy information
- Other business-domain data
- `permissions`
- `kid`
- `iss`
- `aud`

The JWT is an authentication/authorization context, not a replacement for domain data.

## 9. Apartment and Unit Relationships

A valid JWT proves authenticated identity and role. It does **not** prove apartment/unit ownership, tenancy, occupancy, or another business relationship.

If an endpoint requires a particular unit relationship, the relevant domain service must validate it through its documented API. Unit relationship data must not be placed in the JWT.

## 10. Key Ownership

### Identity Access Service

Owns:

```text
Identity Access Private Key
Identity Access Public Key
```

The private key signs User JWTs and remains only in Identity Access.

The public key is shared with the Gateway.

Identity Access does not need public keys for other services because it does not authenticate service-to-service calls.

### API Gateway

Owns:

```text
Gateway Private Key
Gateway Public Key
```

The private key signs new Gateway JWTs and remains only in the Gateway.

The public key is shared with backend services.

The Gateway also stores the public keys of trusted services so it can verify incoming Service JWTs.

### Backend Service

Each service owns its own:

```text
Service Private Key
Service Public Key
```

The private key is used when the service creates a Service JWT for an internal request.

The public key is shared with the Gateway.

A backend service does not need another backend service's key.

## 11. Trust Relationships

```text
Identity Access Public Key → API Gateway

Service A Public Key → API Gateway
Service B Public Key → API Gateway
...

Gateway Public Key → All Backend Services
```

Therefore:

- Gateway trusts Identity Access for user tokens.
- Gateway trusts registered services for service tokens.
- Backend services trust the Gateway for forwarded requests.
- Service B does not need to trust Service A directly.

# 12. User Login Workflow

```text
Frontend
   │ credentials
   ▼
API Gateway
   │
   ▼
Identity Access Service
```

Identity Access validates the credentials and, if valid, creates and signs a User JWT with its private key.

Example:

```json
{
  "sub": "user_123",
  "type": "user",
  "roles": ["TENANT"],
  "iat": 1750000000,
  "exp": 1750001800
}
```

The token is returned to the frontend.

# 13. User Request Workflow

The frontend sends:

```http
Authorization: Bearer <USER_JWT>
```

The Gateway receives and validates the token.

## Gateway validation sequence

```text
1. Parse JWT structure
2. Confirm algorithm = RS256
3. Verify signature using Identity Access Public Key
4. Check type = user
5. Check expiration (exp)
6. Validate required claims
7. Apply Gateway-level authorization/routing rules
8. Create a new Gateway JWT
```

**Claims must never be trusted before successful signature verification.**

# 14. Gateway Re-signing User JWT

After successful verification, the Gateway creates a **new JWT** and signs it with the Gateway private key.

Example:

```json
{
  "sub": "user_123",
  "type": "user",
  "roles": ["TENANT"],
  "iat": 1750000000,
  "exp": 1750000300
}
```

The Gateway should copy only trusted, required information from the verified Identity Access token. It must never blindly copy claims from an unverified token.

The original User JWT is not forwarded to the backend in this design.

# 15. Backend User JWT Verification

The Gateway sends:

```http
Authorization: Bearer <GATEWAY_JWT>
```

The backend service verifies it using the Gateway public key.

Sequence:

```text
1. Parse JWT structure
2. Confirm algorithm = RS256
3. Verify Gateway signature
4. Check type = user
5. Check expiration (exp)
6. Validate required claims
7. Perform role authorization
8. Process request
```

# 16. User Authorization

Authentication answers:

> Who is this?

Authorization answers:

> Is this user allowed to perform this operation?

Example:

```text
Signature valid
    ↓
type = user
    ↓
sub = user_123
    ↓
roles = TENANT
    ↓
Check endpoint role requirement
    ↓
Allowed / Denied
```

# 17. Internal Service Authentication

For an internal call:

```text
Service A
   ↓
API Gateway
   ↓
Service B
```

Service A creates and signs a Service JWT using its own private key.

Example:

```json
{
  "sub": "resident-management-service",
  "type": "service",
  "iat": 1750000000,
  "exp": 1750000300
}
```

# 18. Service A → Gateway Workflow

Service A sends:

```http
Authorization: Bearer <SERVICE_A_JWT>
```

The Gateway validates:

```text
1. Parse JWT structure
2. Confirm algorithm = RS256
3. Identify the claimed service from sub
4. Select the registered public key for that service
5. Verify the JWT signature
6. Check type = service
7. Check expiration (exp)
8. Validate required claims
9. Check whether the service is allowed to make the requested call
10. Create a new Gateway JWT
```

The Gateway must not trust `sub` until the Service JWT signature has been successfully verified.

# 19. Gateway Re-signing Service JWT

After successful verification, the Gateway creates:

```json
{
  "sub": "resident-management-service",
  "type": "service",
  "iat": 1750000000,
  "exp": 1750000300
}
```

and signs it with the Gateway private key.

The original Service A JWT is not forwarded to Service B.

# 20. Gateway → Service B

The Gateway sends:

```http
Authorization: Bearer <GATEWAY_JWT>
```

Service B verifies the token using the Gateway public key. It does not need Service A's public key.

# 21. Backend Verification of Internal JWT

Service B validates:

```text
1. Parse JWT structure
2. Confirm algorithm = RS256
3. Verify Gateway signature
4. Check type = service
5. Check expiration (exp)
6. Validate required claims
7. Identify calling service from sub
8. Check service-level authorization
9. Process request
```

Example:

```text
Signature ✓
type = service ✓
sub = resident-management-service
exp ✓
       ↓
Is resident-management-service allowed
to call this endpoint?
       ↓
YES → process
```

# 22. User vs Internal Request

| Request | JWT | `type` | `sub` represents | Final signer to backend |
|---|---|---|---|---|
| User request | User JWT → Gateway JWT | `user` | User ID | Gateway |
| Internal request | Service JWT → Gateway JWT | `service` | Service name | Gateway |

Therefore:

```text
type = user
    → user authentication context

type = service
    → internal service authentication context
```

# 23. Universal Backend Validation Rule

Every backend service receiving a Gateway JWT follows:

```text
Receive request
      ↓
Extract JWT
      ↓
Validate JWT structure
      ↓
Verify Gateway signature
      ↓
Check type
      ↓
Check expiration
      ↓
Validate required claims
      ↓
Authorize user/service
      ↓
Process request
```

Never authorize based on claims from an invalid or unverified token.

# 24. Authentication vs Authorization

```text
JWT signature verification
        ↓
Authentication
        ↓
Who is this?
        ↓
type + sub + roles
        ↓
Authorization
        ↓
Can they do this?
```

For users, authorization is based on roles and the required business/domain relationship.

For internal requests, authorization is based on the calling service and the endpoint's allowed-service rules.

# 25. Token Validation Rules

JWT validation must follow these rules for every protected request.

### Gateway validation of incoming tokens

```text
1. Require Authorization: Bearer <JWT>
2. Parse the JWT structure
3. Confirm algorithm = RS256
4. Determine the expected trust key
5. Verify the JWT signature
6. Check token type
7. Check expiration (exp)
8. Validate required claims and their types
9. Apply the required authorization/routing rules
10. Create a new Gateway JWT
```

For a User JWT, the Gateway verifies the signature using the Identity Access public key and requires:

```text
type = user
sub = valid user ID
roles = valid role array
iat = valid Unix timestamp
exp = valid Unix timestamp
```

For a Service JWT, the Gateway first determines the claimed service only for the purpose of selecting its registered public key. The `sub` value is not trusted as authenticated identity until signature verification succeeds. After verification, the Gateway requires:

```text
type = service
sub = registered service
iat = valid Unix timestamp
exp = valid Unix timestamp
```

### Backend validation of Gateway tokens

```text
1. Require Authorization: Bearer <JWT>
2. Parse the JWT structure
3. Confirm algorithm = RS256
4. Verify the signature using the Gateway public key
5. Check token type
6. Check expiration (exp)
7. Validate required claims and their types
8. Apply endpoint authorization rules
9. Process the request
```

A backend receiving a user request requires:

```text
type = user
sub = valid user ID
roles = valid role array
iat = valid Unix timestamp
exp = valid Unix timestamp
```

A backend receiving an internal service request requires:

```text
type = service
sub = authenticated calling service
iat = valid Unix timestamp
exp = valid Unix timestamp
```

### Validation failure rules

Reject the request if any of the following occurs:

- Authorization header is missing.
- Bearer token is missing or malformed.
- JWT structure is invalid.
- JWT algorithm is not `RS256`.
- Signature verification fails.
- Token `type` is missing or unexpected.
- Required claims are missing or have invalid types.
- `exp` is missing, invalid, or expired.
- A Service JWT identifies an unregistered service.
- A request attempts to use a User JWT as a Service JWT, or vice versa.

Authentication failures return **HTTP 401 Unauthorized**. A successfully authenticated request that lacks the required authorization returns **HTTP 403 Forbidden**.

**Claims must never be trusted before successful signature verification.**

# 25. HTTP Errors

Use:

```http
401 Unauthorized
```

when authentication fails, including:

- Missing JWT
- Malformed JWT
- Invalid signature
- Unsupported algorithm
- Expired JWT
- Invalid required claims

Use:

```http
403 Forbidden
```

when authentication succeeds but authorization fails, including:

- User role is not allowed
- Calling service is not allowed

# 26. Token Expiration

Recommended lifetimes:

```text
User access JWT: 30 minutes
Service JWT: 5 minutes
Gateway JWT: 5 minutes
```

These values are configurable.

# 27. Role Changes

Roles are included in the User JWT at issuance.

If a user's role changes after a token is issued, an already-issued token can contain the previous role until expiration.

The 30-minute access-token lifetime limits this window.

For sensitive operations, the relevant service may perform an additional current-state check through the Identity Access API.

# 28. Logout

Initial logout behavior:

```text
Frontend deletes the User JWT.
```

The JWT remains valid until expiration unless a future revocation mechanism is introduced.

# 29. HTTPS

In deployed environments, use HTTPS for:

```text
Frontend → Gateway
Gateway → Identity Access
Gateway → Backend Service
Service → Gateway
```

JWTs must not be transmitted over unencrypted HTTP in production.

# 30. API Gateway Responsibilities

The Gateway is responsible for:

- Central routing
- User JWT verification
- Service JWT verification
- Gateway JWT creation
- Forwarding authenticated requests
- Authentication-context validation
- Service-level trust enforcement
- Consistent authentication errors

The Gateway is not the owner of user credentials or business-domain data.

# 31. Identity Access Responsibilities

Identity Access is responsible for:

- User authentication
- User management
- Role management
- Creating User JWTs
- Signing User JWTs with its private key
- Providing its public key to the Gateway
- Providing stable APIs required by other services for user/relationship validation

Identity Access does **not** authenticate backend services in the service-to-service flow.

# 32. Backend Service Responsibilities

Every backend service must:

- Protect its endpoints
- Verify Gateway JWT signatures
- Check JWT type
- Check expiration
- Validate required claims
- Apply role authorization for user requests
- Apply service authorization for internal requests
- Never access another service's database directly
- Use documented APIs for cross-service communication

# 33. Service-to-Service Authorization

Authentication identifies the service.

Authorization determines whether the service is allowed to call the endpoint.

Example:

```text
sub = resident-management-service
type = service
        ↓
Check endpoint policy
        ↓
Allowed → continue
Denied  → 403
```

These service authorization rules should be documented with the API contract.

# 34. Environment Variables

## Identity Access Service

```env
SERVICE_NAME=identity-access-service
PORT=3001

JWT_PRIVATE_KEY=...
JWT_ALGORITHM=RS256
JWT_ACCESS_TOKEN_EXPIRES_IN=30m
```

Identity Access does not need public keys for other services.

## API Gateway

```env
SERVICE_NAME=api-gateway
PORT=3000

JWT_ALGORITHM=RS256

IDENTITY_JWT_PUBLIC_KEY=...

GATEWAY_JWT_PRIVATE_KEY=...
GATEWAY_JWT_EXPIRES_IN=5m

RESIDENT_SERVICE_PUBLIC_KEY=...
PROPERTY_SERVICE_PUBLIC_KEY=...
LEASE_SERVICE_PUBLIC_KEY=...
```

The trusted-service key list should match the services registered in Project A.

## Backend Service

Example:

```env
SERVICE_NAME=resident-management-service
PORT=3001

JWT_ALGORITHM=RS256

GATEWAY_JWT_PUBLIC_KEY=...

SERVICE_JWT_PRIVATE_KEY=...
SERVICE_JWT_EXPIRES_IN=5m
```

Each backend service has its own private key.

# 35. Environment Security

Private keys must:

- Never be committed to Git
- Never be placed in source code
- Never be shared between services
- Never be logged
- Never be returned in API responses

Use `.gitignore` for real environment files and commit only an `.env.example` with placeholders.

Example:

```env
JWT_PRIVATE_KEY=<your-private-key>
```

# 36. Public Key Distribution

```text
Identity Access Public Key
        ↓
Gateway

Service A Public Key
        ↓
Gateway

Service B Public Key
        ↓
Gateway

Gateway Public Key
        ↓
All Backend Services
```

Public keys are not secret, but private keys must never be distributed.

# 37. Complete User Flow

```text
                    ┌─────────────────────┐
                    │ Identity Access     │
                    │ Private Key IA      │
                    └──────────┬──────────┘
                               │
                         signs User JWT
                               │
                               ▼
┌──────────┐              ┌─────────────┐
│ Frontend │─────────────>│ API Gateway │
└──────────┘  User JWT     └──────┬──────┘
                                  │
                          Verify Identity Access
                                  │
                          Create Gateway JWT
                                  │
                                  ▼
                           Backend Service
                                  │
                           Verify Gateway JWT
                                  │
                           Authorize user
                                  │
                                  ▼
                              Response
```

# 38. Complete Internal Service Flow

```text
┌───────────────────────────┐
│ Service A                 │
│ Private Key A             │
└─────────────┬─────────────┘
              │
        creates Service JWT
        type = service
              │
              ▼
       ┌─────────────┐
       │ API Gateway │
       └──────┬──────┘
              │
      Verify Service A JWT
              │
      Create NEW Gateway JWT
              │
      Sign with Gateway Private Key
              │
              ▼
       ┌─────────────┐
       │ Service B   │
       └──────┬──────┘
              │
       Verify Gateway JWT
              │
       Authorize Service A
              │
              ▼
          Process
```

# 39. Security Model

```text
USER AUTHENTICATION

Identity Access
    │ signs
    ▼
User JWT (type=user)
    │
    ▼
Gateway
    │ verifies Identity Access signature
    │ creates NEW Gateway JWT
    ▼
Backend Service
    │ verifies Gateway signature
    ▼
User authorization


SERVICE AUTHENTICATION

Service A
    │ signs
    ▼
Service JWT (type=service)
    │
    ▼
Gateway
    │ verifies Service A signature
    │ creates NEW Gateway JWT
    ▼
Service B
    │ verifies Gateway signature
    ▼
Service authorization
```

# 40. Why Gateway Re-signing Is Used

The Gateway re-signing model creates a simple backend trust boundary.

Backend services do not need to trust every other service directly.

Example:

```text
Service A Public Key → Gateway only
Service B Public Key → Gateway only
Gateway Public Key   → All backend services
```

Service B therefore trusts the Gateway's authenticated assertion about the caller.

This is an architectural choice for this project, not a claim that it is the only industry-standard JWT architecture.

# 41. Implementation Checklist

## Identity Access

- [ ] Generate RSA key pair
- [ ] Store private key securely
- [ ] Configure RS256
- [ ] Issue User JWTs
- [ ] Include `sub`
- [ ] Include `type=user`
- [ ] Include `roles`
- [ ] Include `iat`
- [ ] Include `exp`
- [ ] Use 30-minute access-token lifetime
- [ ] Provide public key to Gateway
- [ ] Never expose private key

## API Gateway

- [ ] Store Identity Access public key
- [ ] Store trusted service public keys
- [ ] Store Gateway private key securely
- [ ] Verify User JWTs
- [ ] Verify Service JWTs
- [ ] Check signature before trusting claims
- [ ] Check `type`
- [ ] Check expiration
- [ ] Create new Gateway JWT
- [ ] Sign Gateway JWT with Gateway private key
- [ ] Forward Gateway JWT to backend services
- [ ] Never expose Gateway private key

## Backend Services

- [ ] Store Gateway public key
- [ ] Store service private key
- [ ] Verify Gateway JWT
- [ ] Check signature before trusting claims
- [ ] Check `type`
- [ ] Check expiration
- [ ] Authorize user roles
- [ ] Authorize calling services
- [ ] Create Service JWT for internal calls
- [ ] Send internal calls through Gateway
- [ ] Never share private keys

# 42. Final Standard

```text
USER:

Frontend
   ↓ User JWT (type=user)
API Gateway
   ↓ verifies Identity Access
   ↓ creates NEW Gateway JWT (type=user)
Backend Service
   ↓ verifies Gateway signature
   ↓ authorizes user


INTERNAL:

Service A
   ↓ Service JWT (type=service)
API Gateway
   ↓ verifies Service A
   ↓ creates NEW Gateway JWT (type=service)
Service B
   ↓ verifies Gateway signature
   ↓ authorizes calling service
```

> **Identity Access authenticates users, services authenticate themselves to the Gateway, and the Gateway issues trusted JWTs that backend services verify before authorization.**

# 43. Project Boundary

The Project A assignment requires JWT authentication, role-based access, API Gateway behavior, stable APIs, cross-service integration, and secure service boundaries.

The exact choice to have the Gateway re-sign incoming user and service JWTs is the team's architectural decision within those requirements.

Business-domain authorization, such as verifying a resident's relationship to a particular apartment/unit, remains owned by the relevant domain service and is not replaced by JWT claims.
