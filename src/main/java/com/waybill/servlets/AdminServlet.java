package com.waybill.servlets;

import com.waybill.Db;
import com.waybill.web.ApiException;
import com.waybill.web.ApiServlet;
import com.waybill.web.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@WebServlet("/api/admin/*")
public class AdminServlet extends ApiServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireRole(req, "admin");
        String[] seg = segments(req);
        if (seg.length != 1 || !seg[0].equals("users")) throw new ApiException(404, "Not found");
        Json.send(resp, 200, Db.query("SELECT id, email, role, full_name, city, wallet_balance, avatar_url FROM users ORDER BY id"));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireRole(req, "admin");
        String[] seg = segments(req);
        if (seg.length != 3 || !seg[0].equals("users") || !seg[2].equals("role")) throw new ApiException(404, "Not found");
        String role = str(Json.body(req), "role", 20);
        if (!Set.of("customer", "courier", "admin").contains(role)) throw new ApiException(400, "Invalid role");
        Db.update("UPDATE users SET role = ? WHERE id = ?", role, id(seg[1]));
        Json.send(resp, 200, Map.of("ok", true));
    }
}
