package com.waybill.servlets;

import com.waybill.Pricing;
import com.waybill.web.ApiException;
import com.waybill.web.ApiServlet;
import com.waybill.web.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

@WebServlet("/api/quote")
public class QuoteServlet extends ApiServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        user(req);
        Map<String, Object> body = Json.body(req);
        String service = str(body, "service", 20);
        if (!Pricing.validService(service)) throw new ApiException(400, "Unknown service level");
        BigDecimal weight = ShipmentServlet.decimal(body.get("weightKg"));
        if (weight.signum() == 0) throw new ApiException(400, "weightKg must not be zero");
        Json.send(resp, 200, Map.of("price", Pricing.price(weight, service), "currency", "EUR"));
    }
}
