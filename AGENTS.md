# Lease Occupancy Service — Repository Guidance

Project A's canonical target contracts are the five project documents in `../../../API final` supplied by the project team. The service contract is `LEASE-OCCUPANCY-SERVICE.md`; shared API, JWT, architecture, and provider/consumer rules come from the four `PROJECT-A-*` documents. Check their current versions before changing behavior. Existing implementation and older documentation are not contract authority.

Service identity: `lease-occupancy-service`, Java package `kln.ams.leaseoccupancy`, MySQL schema `lease_occupancy_db`, Java 21, Spring Boot 4.1.1, Maven. It owns only leases, occupants, occupancy, and their history. Use documented provider APIs through the Gateway. Never query another service's database or put another service's authoritative data in this schema.

The canonical domain inventory has 11 public endpoints and 3 internal endpoints. Public requests require Gateway-signed user JWTs; internal requests require Gateway-signed service JWTs and an allowed caller from the Cross-Service API Registry. The Gateway public key and this service's private key are required deployment secrets. No real keys or credentials belong in Git.

Known integration dependency: the available registry names `RES-INT-001` and `RES-INT-002` but does not define their response schemas. Owner/resident scope and `LEASE-INT-003` need an authoritative user-to-resident mapping from the Resident Management provider. Until that contract is available, fail closed with `503 DEPENDENCY_UNAVAILABLE`; do not assume user UUID equals resident UUID.

Run `./mvnw clean verify` before committing. The unit/controller suite uses H2 and mocked providers. MySQL Flyway, Gateway JWT, health, and Docker integration require a live integrated environment. Preserve existing user changes when working in this repository.

Use feature branches and conventional commit prefixes when committing. Keep OpenAPI, Postman, tests, README, and the canonical contract synchronized. Cross-service contract changes require provider and consumer coordination before implementation.
