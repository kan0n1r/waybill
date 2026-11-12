package com.waybill.web;

import com.waybill.Auth;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Set;

@WebFilter("/api/*")
public class AuthFilter implements Filter {
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/auth/bootstrap", "/api/auth/login", "/api/auth/logout", "/api/auth/forgot", "/api/auth/reset",
            "/api/track", "/api/webhooks/carrier");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();

        req.setAttribute(Auth.REQUEST_ATTR, Auth.resolve(req));
        boolean isPublic = PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
        if (!isPublic && req.getAttribute(Auth.REQUEST_ATTR) == null) {
            Json.error(resp, 401, "Authentication required");
            return;
        }
        chain.doFilter(request, response);
    }
}
