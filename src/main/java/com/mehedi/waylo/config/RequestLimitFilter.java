package com.mehedi.waylo.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** Single-instance limits. A shared limiter is required before horizontal scaling. */
@Component
@Order(-110)
public class RequestLimitFilter extends OncePerRequestFilter {
    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .maximumSize(20000).expireAfterWrite(Duration.ofMinutes(1)).build();
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String path=request.getServletPath();
        if ("POST".equals(request.getMethod()) && !path.startsWith("/payments/sslcommerz/")) {
            boolean auth=path.equals("/login")||path.equals("/register")||path.equals("/forgot-password")||path.equals("/api/v1/auth/token");
            int limit=auth?10:90;
            String bucket=request.getRemoteAddr()+":"+(auth?path:"write");
            if(counters.get(bucket,key->new AtomicInteger()).incrementAndGet()>limit) {
                response.setStatus(429);response.setHeader("Retry-After","60");
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Too many requests. Wait one minute and try again.");return;
            }
        }
        chain.doFilter(request,response);
    }
}
