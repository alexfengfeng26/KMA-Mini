package com.kma.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** 为 API、日志与下游调用关联统一 traceId。 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);
        try { chain.doFilter(request, response); } finally { MDC.remove("traceId"); }
    }
}

