package com.sporty.eventstream.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


@Slf4j
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incomingTraceId = request.getHeader(TraceIdContext.HEADER_NAME);
        String traceId = StringUtils.hasText(incomingTraceId) ? incomingTraceId : TraceIdContext.generate();
        TraceIdContext.setTraceId(traceId);
        response.setHeader(TraceIdContext.HEADER_NAME, traceId);
        log.debug("Trace assigned for request: method={}, uri={}, traceId={}", request.getMethod(), request.getRequestURI(), traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceIdContext.clear();
        }
    }
}

