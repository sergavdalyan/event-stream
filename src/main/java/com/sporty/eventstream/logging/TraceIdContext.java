package com.sporty.eventstream.logging;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;


public final class TraceIdContext {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER_NAME = "X-Trace-Id";

    private TraceIdContext() {
    }

    public static Optional<String> currentTraceId() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }

    public static String ensureTraceId() {
        return currentTraceId().orElseGet(() -> {
            String traceId = generate();
            setTraceId(traceId);
            return traceId;
        });
    }

    public static void setTraceId(String traceId) {
        if (StringUtils.hasText(traceId)) {
            MDC.put(MDC_KEY, traceId);
        }
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}

