package com.waybill.servlets;

import com.waybill.Db;
import com.waybill.Pricing;
import com.waybill.User;
import com.waybill.web.ApiException;
import com.waybill.web.ApiServlet;
import com.waybill.web.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@WebServlet("/api/shipments/*")
public class ShipmentServlet extends ApiServlet {
    static final Set<String> STATUSES = Set.of("created", "picked_up", "in_transit", "out_for_delivery", "delivered", "exception");
    private static final String COLUMNS = "id, owner_id, tracking_code, recipient_name, recipient_city, recipient_address, "
            + "weight_kg, service, price, status, instructions, created_at, updated_at";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = user(req);
        String[] seg = segments(req);
        if (seg.length == 0) {
            List<Map<String, Object>> rows = u.isStaff()
                    ? Db.query("SELECT " + COLUMNS + " FROM shipments ORDER BY id DESC")
                    : Db.query("SELECT " + COLUMNS + " FROM shipments WHERE owner_id = ? ORDER BY id DESC", u.id());
            Json.send(resp, 200, rows);
        } else if (seg.length == 1) {
            Map<String, Object> s = Db.one("SELECT " + COLUMNS + " FROM shipments WHERE id = ?", id(seg[0]));
            if (s == null) throw new ApiException(404, "Shipment not found");
            Map<String, Object> out = new HashMap<>(s);
            out.put("events", Db.query("SELECT status, location, note, created_at FROM events WHERE shipment_id = ? ORDER BY id", s.get("id")));
            Json.send(resp, 200, out);
        } else {
            throw new ApiException(404, "Not found");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = user(req);
        String[] seg = segments(req);
        if (seg.length == 0) {
            create(req, resp, u);
        } else if (seg.length == 2 && seg[1].equals("instructions")) {
            Map<String, Object> s = owned(u, id(seg[0]));
            Db.update("UPDATE shipments SET instructions = ? WHERE id = ?", str(Json.body(req), "text", 500), s.get("id"));
            Json.send(resp, 200, Map.of("ok", true));
        } else if (seg.length == 2 && seg[1].equals("status")) {
            requireRole(req, "courier", "admin");
            Map<String, Object> body = Json.body(req);
            String status = str(body, "status", 30);
            if (!STATUSES.contains(status)) throw new ApiException(400, "Unknown status");
            long id = id(seg[0]);
            if (Db.one("SELECT id FROM shipments WHERE id = ?", id) == null) throw new ApiException(404, "Shipment not found");
            Db.update("UPDATE shipments SET status = ?, updated_at = now() WHERE id = ?", status, id);
            Db.update("INSERT INTO events (shipment_id, status, location) VALUES (?,?,?)", id, status, body.get("location") instanceof String l ? l : null);
            Json.send(resp, 200, Map.of("ok", true));
        } else {
            throw new ApiException(404, "Not found");
        }
    }

    private Map<String, Object> owned(User u, long id) {
        Map<String, Object> s = Db.one("SELECT id, owner_id FROM shipments WHERE id = ?", id);
        if (s == null || (((Number) s.get("owner_id")).longValue() != u.id() && !u.isStaff())) {
            throw new ApiException(404, "Shipment not found");
        }
        return s;
    }

    private void create(HttpServletRequest req, HttpServletResponse resp, User u) throws IOException {
        Map<String, Object> body = Json.body(req);
        String name = str(body, "recipientName", 120);
        String city = str(body, "recipientCity", 80);
        String address = str(body, "recipientAddress", 200);
        String service = str(body, "service", 20);
        if (!Pricing.validService(service)) throw new ApiException(400, "Unknown service level");
        BigDecimal weight = decimal(body.get("weightKg"));
        if (weight.signum() == 0) throw new ApiException(400, "weightKg must not be zero");
        BigDecimal price = Pricing.price(weight, service);

        try (Connection c = Db.conn()) {
            c.setAutoCommit(false);
            try {
                BigDecimal balance;
                try (PreparedStatement ps = c.prepareStatement("SELECT wallet_balance FROM users WHERE id = ? FOR UPDATE")) {
                    ps.setLong(1, u.id());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        balance = rs.getBigDecimal(1);
                    }
                }
                if (price.compareTo(balance) > 0) throw new ApiException(402, "Insufficient wallet balance");
                long id;
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO shipments (owner_id, recipient_name, recipient_city, recipient_address, weight_kg, service, price) "
                        + "VALUES (?,?,?,?,?,?,?) RETURNING id")) {
                    Db.bind(ps, u.id(), name, city, address, weight, service, price);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        id = rs.getLong(1);
                    }
                }
                exec(c, "UPDATE shipments SET tracking_code = ? WHERE id = ?", "WB-" + (100000 + id), id);
                exec(c, "INSERT INTO events (shipment_id, status, location) VALUES (?,?,?)", id, "created", u.city());
                exec(c, "UPDATE users SET wallet_balance = wallet_balance - ? WHERE id = ?", price, u.id());
                c.commit();
                Json.send(resp, 201, Map.of("id", id, "trackingCode", "WB-" + (100000 + id), "price", price));
            } catch (RuntimeException | SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void exec(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Db.bind(ps, params);
            ps.executeUpdate();
        }
    }

    static BigDecimal decimal(Object o) {
        try {
            if (o instanceof Number n) return new BigDecimal(n.toString());
            if (o instanceof String s) return new BigDecimal(s.trim());
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new ApiException(400, "weightKg must be a number");
    }
}
