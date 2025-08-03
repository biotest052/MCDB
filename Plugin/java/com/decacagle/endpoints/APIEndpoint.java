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

    // Helper methods (conversions and parsing)

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
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
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
        StringBuilder buffer = new StringBuilder(12288);
        try {
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);

            int b;

            while ((b = br.read()) != -1) {
                buffer.append((char) b);
            }

            br.close();
            isr.close();
        } catch (UnsupportedEncodingException e) {
            logger.info(e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            logger.info(e.getMessage());
            e.printStackTrace();
        }

        return buffer.toString();

    }

}
