package com.inetbank.o_back_gateway.config;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Owns browser CORS at the Gateway boundary. setHeader deliberately replaces
 * a downstream CORS value so the browser receives only one allowed origin.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayCorsConfig extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "http://localhost:8000",
                "http://localhost:8001",
            "http://127.0.0.1:8000",
                "http://127.0.0.1:8001",
            "http://localhost:5500",
            "http://127.0.0.1:5500",
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String origin = request.getHeader("Origin");
        boolean allowedOrigin = origin != null && ALLOWED_ORIGINS.contains(origin);

        if (allowedOrigin && "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            applyCorsHeaders(response, origin);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        HttpServletResponse singleOriginResponse =
                new SingleOriginResponseWrapper(response);

        // Set this before the routed service is invoked, so even an error
        // response retains a valid browser CORS header.
        if (allowedOrigin) {
            applyCorsHeaders(singleOriginResponse, origin);
        }

        filterChain.doFilter(request, singleOriginResponse);

        if (allowedOrigin) {
            applyCorsHeaders(singleOriginResponse, origin);
        }
    }

    private void applyCorsHeaders(HttpServletResponse response, String origin) {
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept");
        response.setHeader("Access-Control-Expose-Headers", "Authorization");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Vary", "Origin");
    }

    /**
     * A routed service can add its own CORS header. Converting addHeader to
     * setHeader for this one response header prevents the invalid duplicated
     * header that browsers reject.
     */
    private static final class SingleOriginResponseWrapper
            extends HttpServletResponseWrapper {

        private SingleOriginResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void addHeader(String name, String value) {
            if ("Access-Control-Allow-Origin".equalsIgnoreCase(name)) {
                super.setHeader(name, value);
                return;
            }
            super.addHeader(name, value);
        }
    }
}
