package com.ams.leaseoccupancy.config;

import org.slf4j.MDC;

/** Read-side companion to {@link RequestIdFilter} — fetches the current request's trace ID. */
public final class RequestContext {

    static final String MDC_KEY = "requestId";

    private RequestContext() {
    }

    public static String getRequestId() {
        String requestId = MDC.get(MDC_KEY);
        return requestId != null ? requestId : "unknown";
    }
}
