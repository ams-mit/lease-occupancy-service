/**
 * JPA entities: Lease, Occupant, OccupancyStatus. Foreign entities owned by other
 * services (units, tenants, residents) are referenced only via synthetic UUID columns,
 * never via JPA relationships across service boundaries.
 */
package com.ams.leaseoccupancy.entity;
