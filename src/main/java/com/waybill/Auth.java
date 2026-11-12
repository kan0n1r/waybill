package com.waybill;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

public final class Auth {
    public static final String REQUEST_ATTR = "wb.user";
    public static final String REMEMBER_COOKIE = "wb_remember";

    public static User resolve(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("uid") instanceof Long id) {
            return load(id);
        }
        String token = cookie(req, REMEMBER_COOKIE);
        return token == null ? null : fromRememberToken(token);
    }

    public static User load(long id) {
        Map<String, Object> r = Db.one(
                "SELECT id, email, role, full_name, city, avatar_url FROM users WHERE id = ?", id);
        if (r == null) return null;
        return new User(((Number) r.get("id")).longValue(), (String) r.get("email"), (String) r.get("role"),
                (String) r.get("full_name"), (String) r.get("city"), (String) r.get("avatar_url"));
    }

    public static String rememberToken(long userId) {
        String payload = userId + ":" + (System.currentTimeMillis() + Config.REMEMBER_TTL_MS);
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return b64 + "." + Crypto.hmacHex(Config.REMEMBER_KEY, b64);
    }

    private static User fromRememberToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) return null;
            String expected = Crypto.hmacHex(Config.REMEMBER_KEY, parts[0]);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] f = payload.split(":");
            if (Long.parseLong(f[1]) < System.currentTimeMillis()) return null;
            return load(Long.parseLong(f[0]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String cookie(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) if (c.getName().equals(name)) return c.getValue();
        return null;
    }

    private Auth() {}
}
