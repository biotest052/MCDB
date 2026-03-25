package com.decacagle.endpoints;

import com.decacagle.DecaDB;
import com.decacagle.data.DataUtilities;
import com.decacagle.data.DataWorker;
import com.decacagle.data.MethodResponse;
import com.decacagle.data.TableManager;
import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.World;

import java.util.logging.Logger;

public class AuthHandler extends APIEndpoint {

    private final TableManager tableManager;

    public AuthHandler(Logger logger, World world, DecaDB plugin, DataWorker worker, TableManager tableManager) {
        super(logger, world, plugin, worker);
        this.tableManager = tableManager;
    }

    @Override
    public void handle(HttpExchange exchange) {
        addCorsHeaders(exchange);
        if (preflightCheck(exchange)) return;

        String query = parseExchangeBody(exchange);
        if (query == null) {
            respond(exchange, 413, "Payload Too Large");
            return;
        }

        logger.info("Received auth query");
        parseAuthQuery(exchange, query.strip());
    }

    public void parseAuthQuery(HttpExchange exchange, String query) {
        String[] args = query.split("\\s+");

        if (args.length == 0 || !args[0].equalsIgnoreCase("auth")) {
            respond(exchange, 400, "Bad Request: Auth queries must start with AUTH");
            return;
        }

        if (args.length == 4) {
            if (args[1].equalsIgnoreCase("register")) {
                registerUser(exchange, args[2], DataUtilities.hashString(args[3]));
            } else if (args[1].equalsIgnoreCase("login")) {
                authenticateUser(exchange, args[2], DataUtilities.hashString(args[3]));
            } else {
                respond(exchange, 400, "Bad Request: AUTH {REGISTER|LOGIN} {username} {password}");
            }
        } else if (args.length == 3) {
            if (args[1].equalsIgnoreCase("verify")) {
                verifyAuthToken(exchange, args[2]);
            } else if (args[1].equalsIgnoreCase("logout")) {
                deleteAuthToken(exchange, args[2]);
            } else {
                respond(exchange, 400, "Bad Request: AUTH {VERIFY|LOGOUT} {token}");
            }
        } else {
            respond(exchange, 400, "Bad Request: AUTH {REGISTER|LOGIN} {username} {password}  or  AUTH {VERIFY|LOGOUT} {token}");
        }
    }

    public void registerUser(HttpExchange exchange, String username, String hashedPassword) {
        if (!DataUtilities.isSafeIdentifier(username)) {
            respond(exchange, 400, "Bad Request: Username may only contain letters, digits, and underscores");
            return;
        }

        int usersIndex = worker.getTableIndex("users", 1);
        if (usersIndex != 0) {
            String existing = tableManager.gatherRowsWithCondition(usersIndex, "username", username);
            JsonArray arr = JsonParser.parseString(existing).getAsJsonArray();
            if (!arr.isEmpty()) {
                respond(exchange, 409, "Conflict: Username '" + username + "' is already taken");
                return;
            }
        }

        String userRow = DataUtilities.userRowBuilder(username, hashedPassword);
        MethodResponse userInsertResponse = tableManager.insertRow("users", userRow);

        if (userInsertResponse.hasError()) {
            respond(exchange, userInsertResponse.getStatusCode(), userInsertResponse.getStatusMessage());
            return;
        }

        String newUserWithId = userInsertResponse.getResponse();
        if (newUserWithId == null) {
            respond(exchange, 500, "Internal Server Error: Failed to write new user");
            return;
        }

        JsonObject user = JsonParser.parseString(newUserWithId).getAsJsonObject();
        int userId = user.get("id").getAsInt();

        String authToken = DataUtilities.generateAuthTokenJson();
        JsonObject authTokenObj = JsonParser.parseString(authToken).getAsJsonObject();
        authTokenObj.addProperty("userId", userId);

        MethodResponse authTokenInsertResponse = tableManager.insertRow("authTokens", authTokenObj.toString());
        if (authTokenInsertResponse.hasError()) {
            respond(exchange, authTokenInsertResponse.getStatusCode(), authTokenInsertResponse.getStatusMessage());
            return;
        }

        JsonObject responseObj = new JsonObject();
        responseObj.addProperty("username", user.get("username").getAsString());
        responseObj.addProperty("userId", userId);
        responseObj.addProperty("authToken", authTokenObj.get("token").getAsString());

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        respond(exchange, 200, responseObj.toString());
    }

    public void authenticateUser(HttpExchange exchange, String username, String hashedPassword) {
        int usersIndex = worker.getTableIndex("users", 1);
        if (usersIndex == 0) {
            respond(exchange, 400, "Bad Request: No users table exists");
            return;
        }

        String userString = tableManager.gatherRowsWithCondition(usersIndex, "username", username);
        JsonArray arr = JsonParser.parseString(userString).getAsJsonArray();

        if (arr.isEmpty()) {
            respond(exchange, 401, "Access Denied: Incorrect username or password");
            return;
        }

        JsonObject user = arr.get(0).getAsJsonObject();

        if (!user.get("passHash").getAsString().equals(hashedPassword)) {
            respond(exchange, 401, "Access Denied: Incorrect username or password");
            return;
        }

        int userId = user.get("id").getAsInt();

        String authToken = DataUtilities.generateAuthTokenJson();
        JsonObject authTokenObj = JsonParser.parseString(authToken).getAsJsonObject();
        authTokenObj.addProperty("userId", userId);

        MethodResponse authTokenInsertResponse = tableManager.insertRow("authTokens", authTokenObj.toString());
        if (authTokenInsertResponse.hasError()) {
            respond(exchange, authTokenInsertResponse.getStatusCode(), authTokenInsertResponse.getStatusMessage());
            return;
        }

        JsonObject responseObj = new JsonObject();
        responseObj.addProperty("username", user.get("username").getAsString());
        responseObj.addProperty("userId", userId);
        responseObj.addProperty("authToken", authTokenObj.get("token").getAsString());

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        respond(exchange, 200, responseObj.toString());
    }

    public void verifyAuthToken(HttpExchange exchange, String authToken) {
        MethodResponse response = tableManager.readTableWithCondition("authTokens", "token", authToken);

        if (response.hasError()) {
            respond(exchange, response.getStatusCode(), response.getStatusMessage());
            return;
        }

        JsonArray arr = JsonParser.parseString(response.getResponse()).getAsJsonArray();
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        if (arr.isEmpty()) {
            respond(exchange, 200, "{\"tokenValid\":false}");
            return;
        }

        JsonObject obj = arr.get(0).getAsJsonObject();

        if (!obj.has("expiration")) {
            tableManager.deleteRow("authTokens", obj.get("id").getAsInt());
            respond(exchange, 200, "{\"tokenValid\":false}");
            return;
        }

        if (DataUtilities.isExpired(obj.get("expiration").getAsString())) {
            tableManager.deleteRow("authTokens", obj.get("id").getAsInt());
            respond(exchange, 200, "{\"tokenValid\":false}");
        } else {
            respond(exchange, 200, "{\"tokenValid\":true}");
        }
    }

    public void deleteAuthToken(HttpExchange exchange, String authToken) {
        MethodResponse response = tableManager.readTableWithCondition("authTokens", "token", authToken);

        if (response.hasError()) {
            respond(exchange, response.getStatusCode(), response.getStatusMessage());
            return;
        }

        JsonArray arr = JsonParser.parseString(response.getResponse()).getAsJsonArray();

        if (arr.isEmpty()) {
            respond(exchange, 200, "Token doesn't exist");
            return;
        }

        JsonObject obj = arr.get(0).getAsJsonObject();
        tableManager.deleteRow("authTokens", obj.get("id").getAsInt());
        respond(exchange, 200, "Token removed");
    }
}