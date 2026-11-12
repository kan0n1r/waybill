package com.waybill;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@WebListener
public class AppInit implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            Files.createDirectories(Path.of(Config.EXPORT_DIR));
            try (InputStream in = AppInit.class.getResourceAsStream("/schema.sql")) {
                for (String stmt : new String(in.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                    if (!stmt.isBlank()) Db.update(stmt);
                }
            }
            if (((Number) Db.one("SELECT COUNT(*) AS n FROM users").get("n")).longValue() == 0) seed();
            sce.getServletContext().log("Waybill started");
        } catch (Exception e) {
            throw new IllegalStateException("Initialisation failed", e);
        }
    }

    private void seed() {
        String[][] users = {
                {"admin@waybill.test", "Admin#2024", "admin", "Nina Archer", "Rotterdam"},
                {"courier@waybill.test", "Courier#2024", "courier", "Marco Ricci", "Milan"},
                {"alice@waybill.test", "alice123", "customer", "Alice Johnson", "Hamburg"},
                {"bob@waybill.test", "bob12345", "customer", "Bob Miller", "Krakow"},
                {"carol@waybill.test", "carol2024", "customer", "Carol White", "Vienna"},
                {"dave@waybill.test", "dave2024", "customer", "Dave Brown", "Lyon"},
        };
        for (int n = 0; n < users.length; n++) {
            String[] u = users[n];
            String slug = u[0].substring(0, u[0].indexOf('@'));
            Db.update("INSERT INTO users (email, password_md5, role, full_name, city, phone, avatar_url) VALUES (?,?,?,?,?,?,?)",
                    u[0], Crypto.md5Hex(u[1]), u[2], u[3], u[4], "+4915100000" + (10 + n), "/img/avatars/" + slug + ".svg");
        }
        Object[][] shipments = {
                {3, "Hannah Weber", "Berlin", "Kastanienallee 12", "2.50", "standard", "9.00", "in_transit", "Please ring twice."},
                {4, "Piotr Nowak", "Warsaw", "ul. Zlota 44", "1.00", "economy", "5.70", "created", null},
                {5, "Elena Fischer", "Prague", "Vinohradska 8", "12.00", "express", "42.90", "delivered", "Leave with the neighbour at flat 12. Gate code 4471."},
                {6, "Luc Moreau", "Lviv", "vul. Franka 3", "4.20", "standard", "12.06", "out_for_delivery", null},
                {3, "Marta Kowalska", "Prague", "Narodni 21", "0.80", "economy", "5.46", "delivered", null},
        };
        String[][] flows = {
                {"created:Hamburg", "picked_up:Hamburg", "in_transit:Hannover"},
                {"created:Krakow"},
                {"created:Vienna", "picked_up:Vienna", "in_transit:Brno", "out_for_delivery:Prague", "delivered:Prague"},
                {"created:Lyon", "picked_up:Lyon", "in_transit:Munich", "out_for_delivery:Lviv"},
                {"created:Hamburg", "picked_up:Hamburg", "in_transit:Dresden", "delivered:Prague"},
        };
        for (int i = 0; i < shipments.length; i++) {
            Object[] s = shipments[i];
            long id = ((Number) Db.one("INSERT INTO shipments (owner_id, recipient_name, recipient_city, recipient_address, weight_kg, service, price, status, instructions) "
                    + "VALUES (?,?,?,?,?::numeric,?,?::numeric,?,?) RETURNING id", s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7], s[8]).get("id")).longValue();
            Db.update("UPDATE shipments SET tracking_code = ? WHERE id = ?", "WB-" + (100000 + id), id);
            for (String f : flows[i]) {
                String[] p = f.split(":");
                Db.update("INSERT INTO events (shipment_id, status, location) VALUES (?,?,?)", id, p[0], p[1]);
            }
        }
    }
}
