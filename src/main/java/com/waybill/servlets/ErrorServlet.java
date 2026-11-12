package com.waybill.servlets;

import com.waybill.web.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/error")
public class ErrorServlet extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Object code = req.getAttribute("jakarta.servlet.error.status_code");
        int status = code instanceof Integer i ? i : 500;
        Json.error(resp, status, status == 404 ? "Not found" : "Internal server error");
    }
}
