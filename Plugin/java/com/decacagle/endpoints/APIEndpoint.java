package com.decacagle.endpoints;

import com.decacagle.DecaDB;
import com.decacagle.data.DataWorker;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public abstract class APIEndpoint implements HttpHandler {

    protected static final int MAX_BODY_BYTES = 1024 * 1024 * 10;

    public Logger logger;
    public World world;
    public DecaDB plugin;
    public DataWorker worker;

    public APIEndpoint(Logger logger, World world, DecaDB plugin, DataWorker worker) {
        this.logger = logger;
        this.world = world;
        this.plugin = plugin;
        this.worker = worker;
    }

    public boolean preflightCheck(HttpExchange exchange) {
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            try {
                exchange.sendResponseHeaders(200, -1);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    public void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Admin-Key");
    }

    public void respond(HttpExchange exchange, int status, String message) {
        try {
            byte[] response = message.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void respondWithBytes(HttpExchange exchange, int status, byte[] response) {
        try {
            exchange.sendResponseHeaders(status, response.length);
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void runSynchronously(Runnable runnable) {
        try {
            Bukkit.getScheduler().runTask(plugin, runnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String parseExchangeBody(HttpExchange exchange) {
        try {
            InputStream is = exchange.getRequestBody();
            byte[] data = is.readNBytes(MAX_BODY_BYTES + 1);
            if (data.length > MAX_BODY_BYTES) {
                return null;
            }
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warning("Failed to read request body: " + e.getMessage());
            return "";
        }
    }

    public boolean isAdminRequest(HttpExchange exchange) {
        String adminKey = plugin.getAdminKey();
        if (adminKey == null || adminKey.isEmpty()) {
            return false;
        }
        String provided = exchange.getRequestHeaders().getFirst("X-Admin-Key");
        return adminKey.equals(provided);
    }
}