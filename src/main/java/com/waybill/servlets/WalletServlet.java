package com.waybill.servlets;

import com.waybill.Db;
import com.waybill.User;
import com.waybill.web.ApiException;
import com.waybill.web.ApiServlet;
import com.waybill.web.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

@WebServlet("/api/wallet/*")
public class WalletServlet extends ApiServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = user(req);
        Json.send(resp, 200, Map.of("balance", Db.one("SELECT wallet_balance FROM users WHERE id = ?", u.id()).get("wallet_balance"), "currency", "EUR"));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = user(req);
        String[] seg = segments(req);
        if (seg.length != 1 || !seg[0].equals("topup")) throw new ApiException(404, "Not found");
        BigDecimal amount = ShipmentServlet.decimal(Json.body(req).get("amount"));
        if (amount.compareTo(BigDecimal.ONE) < 0 || amount.compareTo(new BigDecimal("500")) > 0) {
            throw new ApiException(400, "Amount must be between 1 and 500");
        }
        Db.update("UPDATE users SET wallet_balance = wallet_balance + ? WHERE id = ?", amount, u.id());
        Json.send(resp, 200, Map.of("balance", Db.one("SELECT wallet_balance FROM users WHERE id = ?", u.id()).get("wallet_balance")));
    }
}
