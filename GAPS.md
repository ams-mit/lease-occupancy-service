# Implementation Gaps

## Missing Occupancy Features

The following features related to Occupancy overlap and capacity logic are missing because the `Occupant` entity and its corresponding service logic (Group B physical occupancies) are not yet implemented.

1. **Standard 1-to-1 overlap rejection (409 Conflict)**
   - **What's missing**: Logic to prevent creating a physical occupancy record that overlaps in date range with an existing occupant in a 1-to-1 unit.
   - **Required files**: `OccupancyService.java`, `OccupancyController.java`, `Occupancy.java`.
   - **Ticket**: Traces back to the Occupancy overlap and capacity feature ticket.

2. **Multi-occupancy under capacity (Success)**
   - **What's missing**: Logic to allow multiple occupants in a unit that supports it, provided the total occupants are under the capacity limit.
   - **Required files**: `OccupancyService.java`, `OccupancyController.java`, `Occupancy.java`.
   - **Ticket**: Traces back to the Occupancy overlap and capacity feature ticket.

3. **Capacity breach rejection (422)**
   - **What's missing**: Logic to reject a new occupancy creation with a 422 Unprocessable Entity if it would exceed the unit's capacity limit.
   - **Required files**: `OccupancyService.java`, `OccupancyController.java`, `Occupancy.java`.
   - **Ticket**: Traces back to the Occupancy overlap and capacity feature ticket.
