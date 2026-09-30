# Lease Occupancy Postman collection

`lease-occupancy-service.postman_collection.json` contains the 11 public and 3 internal canonical v1 domain APIs. It uses the API Gateway at `http://localhost:8080/api/v1` by default.

Set `user_jwt` to an Identity Access user token for public calls and `service_jwt` to a token signed by an allowed calling service for internal calls. Set the UUID variables to records in the integrated environment. Every request includes `X-Request-ID`; the collection checks the HTTP status, success envelope, and propagated request ID.

The complete roles, allowed service callers, validation rules, and error codes are in `LEASE-OCCUPANCY-SERVICE.md` and the Project A registry. Public read access for owners and residents depends on the Resident Management relationship provider contract.
