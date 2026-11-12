package com.waybill.servlets;

import com.waybill.Db;
import com.waybill.web.ApiException;
import com.waybill.web.ApiServlet;
import com.waybill.web.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/track/*")
public class TrackServlet extends ApiServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] seg = segments(req);
        if (seg.length == 1) {
            Map<String, Object> s = Db.one("SELECT id, tracking_code, status, recipient_city, updated_at FROM shipments WHERE tracking_code = ?", seg[0].toUpperCase());
            if (s == null) throw new ApiException(404, "No parcel with that tracking code");
            Map<String, Object> out = new HashMap<>(s);
            out.put("events", Db.query("SELECT status, location, created_at FROM events WHERE shipment_id = ? ORDER BY id", s.get("id")));
            out.remove("id");
            Json.send(resp, 200, out);
            return;
        }
        String q = req.getParameter("q");
        if (q == null || q.isBlank()) {
            Json.send(resp, 200, List.of());
            return;
        }
        String sql = "SELECT tracking_code, status, recipient_city AS city, updated_at FROM shipments "
                + "WHERE tracking_code LIKE '%" + q + "%' OR recipient_city ILIKE '%" + q + "%' "
                + "ORDER BY updated_at DESC LIMIT 20";
        try (Connection c = Db.conn(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            Json.send(resp, 200, Db.rows(rs));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
