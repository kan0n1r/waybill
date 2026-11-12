package com.waybill.servlets;

import com.waybill.Auth;
import com.waybill.Db;
import com.waybill.User;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import static com.waybill.Html.esc;

@WebServlet("/shipments/view")
public class ShipmentPageServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        User u = Auth.resolve(req);
        if (u == null) {
            resp.setStatus(401);
            out.print("Please sign in first.");
            return;
        }
        long id;
        try {
            id = Long.parseLong(req.getParameter("id"));
        } catch (NumberFormatException e) {
            resp.setStatus(400);
            out.print("Invalid shipment id");
            return;
        }
        Map<String, Object> s = Db.one("SELECT s.*, o.full_name AS owner_name FROM shipments s JOIN users o ON o.id = s.owner_id WHERE s.id = ?", id);
        if (s == null || (((Number) s.get("owner_id")).longValue() != u.id() && !u.isStaff())) {
            resp.setStatus(404);
            out.print("Shipment not found");
            return;
        }
        List<Map<String, Object>> events = Db.query("SELECT status, location, created_at FROM events WHERE shipment_id = ? ORDER BY id DESC", id);

        StringBuilder timeline = new StringBuilder();
        for (Map<String, Object> e : events) {
            timeline.append("<li><b>").append(esc(String.valueOf(e.get("status")).replace('_', ' '))).append("</b>")
                    .append("<span>").append(esc(e.get("location"))).append("</span>")
                    .append("<small>").append(esc(String.valueOf(e.get("created_at")).substring(0, 16))).append("</small></li>");
        }
        String instructions = s.get("instructions") == null ? "" : (String) s.get("instructions");

        out.print("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>Waybill - " + esc(s.get("tracking_code")) + "</title>"
                + "<link rel=\"icon\" href=\"/img/favicon.svg\" type=\"image/svg+xml\"><link rel=\"stylesheet\" href=\"/style.css\"></head><body>"
                + "<div class=\"doc\"><div class=\"topline\"><a href=\"/app.html#/shipments\">&larr; All shipments</a>"
                + "<a href=\"/app.html\" class=\"brand\"><img src=\"/img/logo-mark.svg\" width=\"26\" height=\"26\" alt=\"\"><strong>Waybill</strong></a></div>"
                + "<h1>" + esc(s.get("tracking_code")) + "</h1>"
                + "<div class=\"meta\"><span class=\"chip " + esc(s.get("status")) + "\">" + esc(String.valueOf(s.get("status")).replace('_', ' ')) + "</span>"
                + "<span>" + esc(s.get("service")) + " &middot; " + esc(s.get("weight_kg")) + " kg &middot; EUR " + esc(s.get("price")) + "</span></div>"
                + "<div class=\"grid two\"><section class=\"panel\"><h2>Recipient</h2><p><b>" + esc(s.get("recipient_name")) + "</b><br>"
                + esc(s.get("recipient_address")) + "<br>" + esc(s.get("recipient_city")) + "</p><p class=\"muted\">Sent by " + esc(s.get("owner_name")) + "</p></section>"
                + "<section class=\"panel\"><h2>Delivery instructions</h2><div class=\"instructions\">" + instructions + "</div></section></div>"
                + "<section class=\"panel\" style=\"margin-top:18px\"><h2>Tracking history</h2><ol class=\"timeline\">" + timeline + "</ol></section>"
                + "</div></body></html>");
    }
}
