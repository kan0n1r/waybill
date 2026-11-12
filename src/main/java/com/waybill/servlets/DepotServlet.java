package com.waybill.servlets;

import com.waybill.Config;
import com.waybill.Db;
import com.waybill.User;
import com.waybill.web.ApiException;
import com.waybill.web.ApiServlet;
import com.waybill.web.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@WebServlet("/api/depot/*")
public class DepotServlet extends ApiServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireRole(req, "courier", "admin");
        String[] seg = segments(req);
        if (seg.length != 2 || !seg[0].equals("manifest") || !seg[1].equals("import")) throw new ApiException(404, "Not found");

        String xml = new String(req.getInputStream().readNBytes(1 << 20), StandardCharsets.UTF_8);
        List<Map<String, Object>> parsed = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            NodeList nodes = doc.getElementsByTagName("shipment");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                String code = e.getAttribute("code");
                String status = text(e, "status");
                String location = text(e, "location");
                String note = text(e, "note");
                boolean applied = false;
                Map<String, Object> s = Db.one("SELECT id FROM shipments WHERE tracking_code = ?", code);
                if (s != null && ShipmentServlet.STATUSES.contains(status)) {
                    Db.update("UPDATE shipments SET status = ?, updated_at = now() WHERE id = ?", status, s.get("id"));
                    Db.update("INSERT INTO events (shipment_id, status, location, note) VALUES (?,?,?,?)", s.get("id"), status, location, note);
                    applied = true;
                }
                parsed.add(Map.of("code", code, "status", status, "location", location, "note", note, "applied", applied));
            }
        } catch (javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException e) {
            throw new ApiException(400, "Manifest is not valid XML");
        }
        Json.send(resp, 200, Map.of("shipments", parsed));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireRole(req, "courier", "admin");
        String[] seg = segments(req);
        if (seg.length != 2 || !seg[0].equals("manifest") || !seg[1].equals("export")) throw new ApiException(404, "Not found");

        String city = req.getParameter("city");
        String name = req.getParameter("name") == null ? "manifest" : req.getParameter("name");
        List<Map<String, Object>> rows = city == null || city.isBlank()
                ? Db.query("SELECT tracking_code, recipient_name, recipient_city, status FROM shipments ORDER BY id")
                : Db.query("SELECT tracking_code, recipient_name, recipient_city, status FROM shipments WHERE recipient_city = ? ORDER BY id", city);
        String csv = "tracking_code,recipient_name,recipient_city,status\n" + rows.stream()
                .map(r -> r.get("tracking_code") + "," + r.get("recipient_name") + "," + r.get("recipient_city") + "," + r.get("status"))
                .collect(Collectors.joining("\n"));
        Files.writeString(Path.of(Config.EXPORT_DIR, "latest.csv"), csv);

        String cmd = "tar -czf " + Config.EXPORT_DIR + "/" + name + ".tgz -C " + Config.EXPORT_DIR + " latest.csv && ls -l " + Config.EXPORT_DIR + "/" + name + ".tgz";
        Process p = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
        String output;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            output = r.lines().collect(Collectors.joining("\n"));
        }
        try {
            if (p.waitFor() != 0) throw new ApiException(500, "Export failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(500, "Export interrupted");
        }
        Json.send(resp, 200, Map.of("archive", name + ".tgz", "rows", rows.size(), "details", output));
    }

    private static String text(Element parent, String tag) {
        NodeList l = parent.getElementsByTagName(tag);
        return l.getLength() == 0 ? "" : l.item(0).getTextContent().trim();
    }
}
