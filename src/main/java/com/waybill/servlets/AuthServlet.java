package com.waybill.servlets;

import com.waybill.Auth;
import com.waybill.Crypto;
import com.waybill.Db;
import com.waybill.User;
import com.waybill.web.ApiException;
import com.waybill.web.ApiServlet;
import com.waybill.web.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/api/auth/*")
public class AuthServlet extends ApiServlet {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] seg = segments(req);
        if (seg.length == 1 && seg[0].equals("bootstrap")) {
            req.getSession(true);
            Json.send(resp, 200, Map.of("ok", true));
        } else if (seg.length == 1 && seg[0].equals("me")) {
            Json.send(resp, 200, profile(user(req)));
        } else {
            throw new ApiException(404, "Not found");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] seg = segments(req);
        if (seg.length != 1) throw new ApiException(404, "Not found");
        switch (seg[0]) {
            case "login" -> login(req, resp);
            case "logout" -> logout(req, resp);
            case "forgot" -> forgot(req, resp);
            case "reset" -> reset(req, resp);
            default -> throw new ApiException(404, "Not found");
        }
    }

    private void login(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body = Json.body(req);
        String email = str(body, "email", 200).toLowerCase();
        String password = str(body, "password", 200);

        Map<String, Object> row = Db.one("SELECT id, password_md5 FROM users WHERE email = ?", email);
        if (row == null || !row.get("password_md5").equals(Crypto.md5Hex(password))) {
            throw new ApiException(401, "Invalid email or password");
        }
        long id = ((Number) row.get("id")).longValue();

        HttpSession session = req.getSession(true);
        session.setAttribute("uid", id);

        if (Boolean.TRUE.equals(body.get("remember"))) {
            Cookie c = new Cookie(Auth.REMEMBER_COOKIE, Auth.rememberToken(id));
            c.setHttpOnly(true);
            c.setPath("/");
            c.setMaxAge(14 * 24 * 3600);
            resp.addCookie(c);
        }
        Json.send(resp, 200, profile(Auth.load(id)));
    }

    private void logout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();
        Cookie c = new Cookie(Auth.REMEMBER_COOKIE, "");
        c.setPath("/");
        c.setMaxAge(0);
        resp.addCookie(c);
        Json.send(resp, 200, Map.of("ok", true));
    }

    private void forgot(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body = Json.body(req);
        String email = str(body, "email", 200).toLowerCase();
        Map<String, Object> row = Db.one("SELECT id FROM users WHERE email = ?", email);
        if (row != null) {
            byte[] raw = new byte[32];
            RANDOM.nextBytes(raw);
            String token = Crypto.hex(raw);
            Db.update("UPDATE users SET reset_hash = ?, reset_expires = ?, reset_attempts = 0 WHERE id = ?",
                    Crypto.sha256Hex(token), Timestamp.from(Instant.now().plusSeconds(900)), row.get("id"));
            getServletContext().log("MAIL to " + email + ": password reset token " + token);
        }
        Json.send(resp, 200, Map.of("message", "If the account exists, a reset link has been sent by email."));
    }

    private void reset(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body = Json.body(req);
        String email = str(body, "email", 200).toLowerCase();
        String token = str(body, "token", 200);
        String newPassword = str(body, "newPassword", 200);
        if (newPassword.length() < 8) throw new ApiException(400, "Password must be at least 8 characters");

        Map<String, Object> row = Db.one("SELECT id, reset_hash, reset_expires, reset_attempts FROM users WHERE email = ?", email);
        if (row == null || row.get("reset_hash") == null) throw new ApiException(400, "Invalid or expired token");
        long id = ((Number) row.get("id")).longValue();
        if (((Number) row.get("reset_attempts")).intValue() >= 5
                || ((Timestamp) row.get("reset_expires")).toInstant().isBefore(Instant.now())) {
            Db.update("UPDATE users SET reset_hash = NULL WHERE id = ?", id);
            throw new ApiException(400, "Invalid or expired token");
        }
        boolean ok = MessageDigest.isEqual(((String) row.get("reset_hash")).getBytes(), Crypto.sha256Hex(token).getBytes());
        if (!ok) {
            Db.update("UPDATE users SET reset_attempts = reset_attempts + 1 WHERE id = ?", id);
            throw new ApiException(400, "Invalid or expired token");
        }
        Db.update("UPDATE users SET password_md5 = ?, reset_hash = NULL WHERE id = ?", Crypto.md5Hex(newPassword), id);
        Json.send(resp, 200, Map.of("message", "Password updated"));
    }

    static Map<String, Object> profile(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.id());
        m.put("email", u.email());
        m.put("role", u.role());
        m.put("fullName", u.fullName());
        m.put("city", u.city());
        m.put("avatarUrl", u.avatarUrl());
        return m;
    }
}
