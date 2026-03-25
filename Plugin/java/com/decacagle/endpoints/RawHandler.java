package com.decacagle.endpoints;

import com.decacagle.DecaDB;
import com.decacagle.data.DataWorker;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.World;

import java.util.logging.Logger;

/**
 * Usage:
 *   POST /admin/raw
 *   X-Admin-Key: your-secret-key
 *   Body: READ {x} {z}
 *         DELETE {x} {z}
 *         WRITE {x} {z} {content}
 */
public class RawHandler extends APIEndpoint {

    public RawHandler(Logger logger, World world, DecaDB plugin, DataWorker worker) {
        super(logger, world, plugin, worker);
    }

    @Override
    public void handle(HttpExchange exchange) {
        addCorsHeaders(exchange);
        if (preflightCheck(exchange)) return;

        if (!isAdminRequest(exchange)) {
            respond(exchange, 401, "Access Denied: Valid X-Admin-Key header required");
            return;
        }

        String body = parseExchangeBody(exchange);
        if (body == null) {
            respond(exchange, 413, "Payload Too Large");
            return;
        }

        body = body.strip();
        if (body.isEmpty()) {
            respond(exchange, 400, "Bad Request: Empty body");
            return;
        }

        final String finalBody = body;
        runSynchronously(() -> handleRaw(exchange, finalBody));
    }

    private void handleRaw(HttpExchange exchange, String body) {
        String[] args = body.split("\\s+", 5);

        if (args.length < 3) {
            respond(exchange, 400, "Bad Request: Format: {READ|DELETE} {x} {z}  or  WRITE {x} {z} {content}");
            return;
        }

        int x, z;
        try {
            x = Integer.parseInt(args[1]);
            z = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            respond(exchange, 400, "Bad Request: x and z must be integers");
            return;
        }

        switch (args[0].toUpperCase()) {
            case "READ" -> {
                String result = worker.readChunk(x, z, false, 1);
                respond(exchange, 200, result);
            }
            case "DELETE" -> {
                worker.deleteChunk(x, z, false, 1);
                respond(exchange, 200, "Deleted chunk at " + x + ", " + z);
            }
            case "WRITE" -> {
                if (args.length < 4) {
                    respond(exchange, 400, "Bad Request: WRITE requires content: WRITE {x} {z} {content}");
                    return;
                }
                String content = args[3];
                worker.deleteChunk(x, z, false, 1);
                worker.writeToChunk(content, x, z, false, 1);
                respond(exchange, 200, "Written to chunk at " + x + ", " + z);
            }
            default -> respond(exchange, 400, "Bad Request: Operation must be READ, DELETE, or WRITE");
        }
    }
}