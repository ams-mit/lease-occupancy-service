# Implementation Gaps

Occupancy overlap and capacity rules are implemented: lease creation and activation check date
overlap and unit capacity from property-unit-service, and `OccupancyService.register` requires an
ACTIVE lease that lists the resident, one active occupancy per resident and unit, and free capacity.
The remaining gaps need contracts from other teams:

1. **Resident and owner access**
   - **What's missing**: Resident Management has not published how a signed-in user maps to resident
     profiles (`RES-INT-002`), so every owner/tenant read (LEASE-002 to LEASE-005, LEASE-009,
     LEASE-010, LEASE-012) fails closed with `503 DEPENDENCY_UNAVAILABLE`. Only management roles can
     use the public endpoints.

2. **Unit status sync**
   - **What's missing**: Activating or ending a lease does not change the unit's status in
     property-unit-service, because the canonical property contract has no status transition route.
   - **Current handling**: The shared frontend shows a unit with an ACTIVE lease as occupied and
     explains that the property record differs.

3. **Maintenance relocation protocol (Rule 4)**
   - **What's missing**: Soft-terminating occupants and relocating them when a unit goes under
     maintenance needs agreed Operations (Group 4) and Billing (Group 3) contracts.

4. **Gateway**
   - **What's missing**: No Gateway public key or routing exists yet, so the service has only been
     exercised with locally minted RS256 tokens.
