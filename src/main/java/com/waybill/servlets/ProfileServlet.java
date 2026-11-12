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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@WebServlet("/api/profile/photo")
public class ProfileServlet extends ApiServlet {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = user(req);
        String url = str(Json.body(req), "url", 500);
        if (!url.matches("(?i)^https?://.+")) throw new ApiException(400, "A valid http(s) URL is required");
        try {
            HttpResponse<byte[]> upstream = CLIENT.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            byte[] data = upstream.body();
            Db.update("UPDATE users SET avatar_url = ? WHERE id = ?", url, u.id());
            Json.send(resp, 200, Map.of(
                    "status", upstream.statusCode(),
                    "contentType", upstream.headers().firstValue("content-type").orElse(""),
                    "size", data.length,
                    "preview", new String(data, 0, Math.min(data.length, 500), StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException | InterruptedException | IOException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ApiException(502, "Could not download the image");
        }
    }
}
