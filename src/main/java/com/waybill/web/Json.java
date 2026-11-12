package com.waybill.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper().configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    public static Map<String, Object> body(HttpServletRequest req) throws IOException {
        try (InputStream in = req.getInputStream()) {
            byte[] bytes = in.readNBytes(1 << 20);
            if (bytes.length == 0) return new HashMap<>();
            return MAPPER.readValue(bytes, new TypeReference<>() {});
        }
    }

    public static void send(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(resp.getWriter(), body);
    }

    public static void error(HttpServletResponse resp, int status, String message) throws IOException {
        send(resp, status, Map.of("error", message));
    }

    private Json() {}
}
