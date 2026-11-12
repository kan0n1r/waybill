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

@WebServlet("/api/webhooks/carrier")
public class WebhookServlet extends ApiServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body = Json.body(req);
        String code = str(body, "trackingCode", 40);
        String status = str(body, "status", 30);
        if (!ShipmentServlet.STATUSES.contains(status)) throw new ApiException(400, "Unknown status");
        Map<String, Object> s = Db.one("SELECT id FROM shipments WHERE tracking_code = ?", code);
        if (s == null) throw new ApiException(404, "Unknown tracking code");
        Db.update("UPDATE shipments SET status = ?, updated_at = now() WHERE id = ?", status, s.get("id"));
        Db.update("INSERT INTO events (shipment_id, status, location, note) VALUES (?,?,?,?)",
                s.get("id"), status, body.get("location") instanceof String l ? l : null, "Carrier update");
        Json.send(resp, 200, Map.of("ok", true));
    }
}
