package com.waybill.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.waybill.Auth;
import com.waybill.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;

public abstract class ApiServlet extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            super.service(req, resp);
        } catch (ApiException e) {
            if (!resp.isCommitted()) Json.error(resp, e.status, e.getMessage());
        } catch (JsonProcessingException e) {
            if (!resp.isCommitted()) Json.error(resp, 400, "Malformed JSON");
        } catch (Exception e) {
            getServletContext().log("Unhandled error", e);
            if (!resp.isCommitted()) Json.error(resp, 500, "Internal server error");
        }
    }

    protected User user(HttpServletRequest req) {
        User u = (User) req.getAttribute(Auth.REQUEST_ATTR);
        if (u == null) throw new ApiException(401, "Authentication required");
        return u;
    }

    protected User requireRole(HttpServletRequest req, String... roles) {
        User u = user(req);
        if (!Arrays.asList(roles).contains(u.role())) throw new ApiException(403, "Forbidden");
        return u;
    }

    protected String[] segments(HttpServletRequest req) {
        String p = req.getPathInfo();
        if (p == null || p.equals("/")) return new String[0];
        return p.substring(1).split("/");
    }

    protected long id(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Invalid id");
        }
    }

    protected String str(java.util.Map<String, Object> body, String key, int max) {
        Object v = body.get(key);
        if (!(v instanceof String s) || s.isBlank() || s.length() > max) throw new ApiException(400, key + " is required");
        return s.trim();
    }
}
