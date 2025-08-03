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

    private TableManager tableManager;

    public AuthHandler(Logger logger, World world, DecaDB plugin, DataWorker worker, TableManager tableManager) {
        super(logger, world, plugin, worker);

        this.tableManager = tableManager;

    }

    public void handle(HttpExchange exchange) {
        addCorsHeaders(exchange);

        if (!preflightCheck(exchange)) {

            String query = parseExchangeBody(exchange);

            logger.info("received auth query: " + query);

            parseAuthQuery(exchange, query);

        }

    }

    public void parseAuthQuery(HttpExchange exchange, String query) {

        String[] args = query.split(" ");

        if (args[0].equalsIgnoreCase("auth")) {
            if (args.length == 4) {
                if (args[1].equalsIgnoreCase("register")) {
                    String username = args[2];
                    String hashedPassword = DataUtilities.hashString(args[3]);

                    registerUser(exchange, username, hashedPassword);


                } else if (args[1].equalsIgnoreCase("login")) {
                    String username = args[2];
                    String hashedPassword = DataUtilities.hashString(args[3]);

                    authenticateUser(exchange, username, hashedPassword);

                } else {
                    respond(exchange, 400, "Bad Request: Improper query, auth queries should be formatted as AUTH {REGISTER or LOGIN} {username} {password}");
                }
            } else if (args.length == 3) {
                if (args[1].equalsIgnoreCase("verify")) {
                    String token = args[2];

                    verifyAuthToken(exchange, token);

                } else if (args[1].equalsIgnoreCase("logout")) {
                    String token = args[2];

                    deleteAuthToken(exchange, token);

                }
            } else {
                respond(exchange, 400, "Bad Request: Improper query, auth queries should be formatted as AUTH {REGISTER or LOGIN} {username} {password}");
            }
        } else {
            respond(exchange, 400, "Bad Request: Improper query, auth queries should be formatted as AUTH {REGISTER or LOGIN} {username} {password}");
        }

    }

    public void registerUser(HttpExchange exchange, String username, String hashedPassword) {
        String userRow = DataUtilities.userRowBuilder(username, hashedPassword);

        MethodResponse userInsertResponse = tableManager.insertRow("users", userRow);

        if (userInsertResponse.hasError()) {
            respond(exchange, userInsertResponse.getStatusCode(), userInsertResponse.getStatusMessage());
        } else {

            String newUserWithId = userInsertResponse.getResponse();

            if (newUserWithId != null) {
                JsonObject user = JsonParser.parseString(newUserWithId).getAsJsonObject();
                int userId = user.get("id").getAsInt();

                String authToken = DataUtilities.generateAuthTokenJson();
                JsonObject authTokenObj = JsonParser.parseString(authToken).getAsJsonObject();
                authTokenObj.addProperty("userId", userId);

                MethodResponse authTokenInsertResponse = tableManager.insertRow("authTokens", authTokenObj.toString());

                if (authTokenInsertResponse.hasError()) {
                    respond(exchange, authTokenInsertResponse.getStatusCode(), authTokenInsertResponse.getStatusMessage());
                } else {
                    JsonObject responseObj = new JsonObject();
                    responseObj.addProperty("username", user.get("username").getAsString());
                    responseObj.addProperty("userId", userId);
                    responseObj.addProperty("authToken", authTokenObj.get("token").getAsString());

                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    respond(exchange, 200, responseObj.toString());
                }
            } else {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                respond(exchange, 500, "Internal Server Error: Failed to write new user to database!");
            }

        }
    }

    public void authenticateUser(HttpExchange exchange, String username, String hashedPassword) {
        int usersIndex = worker.getTableIndex("users", 1);

        if (usersIndex == 0) {
            respond(exchange, 400, "Bad Request: No users table exists!");
        } else {
            String userString = tableManager.gatherRowsWithCondition(usersIndex, "username", username);

            JsonArray arr = JsonParser.parseString(userString).getAsJsonArray();

            if (!arr.isEmpty()) {
                JsonObject user = arr.get(0).getAsJsonObject();

                if (user.get("passHash").getAsString().equals(hashedPassword)) {
                    int userId = user.get("id").getAsInt();

                    String authToken = DataUtilities.generateAuthTokenJson();
                    JsonObject authTokenObj = JsonParser.parseString(authToken).getAsJsonObject();
                    authTokenObj.addProperty("userId", userId);

                    MethodResponse authTokenInsertResponse = tableManager.insertRow("authTokens", authTokenObj.toString());

                    if (authTokenInsertResponse.hasError()) {
                        respond(exchange, authTokenInsertResponse.getStatusCode(), authTokenInsertResponse.getStatusMessage());
                    } else {
                        JsonObject responseObj = new JsonObject();
                        responseObj.addProperty("username", user.get("username").getAsString());
                        responseObj.addProperty("userId", userId);
                        responseObj.addProperty("authToken", authTokenObj.get("token").getAsString());

                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        respond(exchange, 200, responseObj.toString());
                    }
                } else {
                    respond(exchange, 401, "Access Denied: Incorrect username or password!");
                }
            } else {
                respond(exchange, 401, "Access Denied: User doesn't exist!");
            }
        }
    }

    public void verifyAuthToken(HttpExchange exchange, String authToken) {
        MethodResponse response = tableManager.readTableWithCondition("authTokens", "token", authToken);

        if (response.hasError()) {
            respond(exchange, response.getStatusCode(), response.getStatusMessage());
        } else {

            JsonArray arr = JsonParser.parseString(response.getResponse()).getAsJsonArray();

            if (arr.isEmpty()) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                respond(exchange, 200, "{\"tokenValid\":false}");
            } else {
                JsonObject obj = arr.get(0).getAsJsonObject();

                if (obj.has("expiration")) {
                    String expiration = obj.get("expiration").getAsString();

                    boolean isExpired = DataUtilities.isExpired(expiration);

                    if (isExpired) {
                        exchange.getResponseHeaders().add("Content-Type", "application/json");

                        tableManager.deleteRow("authTokens", obj.get("id").getAsInt());

                        respond(exchange, 200, "{\"tokenValid\":false}");

                    } else {
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        respond(exchange, 200, "{\"tokenValid\":true}");
                    }

                } else {
                    tableManager.deleteRow("authTokens", obj.get("id").getAsInt());
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    respond(exchange, 200, "{\"tokenValid\":false}");
                }

            }

        }

    }

    public void deleteAuthToken(HttpExchange exchange, String authToken) {
        MethodResponse response = tableManager.readTableWithCondition("authTokens", "token", authToken);

        if (response.hasError()) {
            respond(exchange, response.getStatusCode(), response.getStatusMessage());
        } else {

            JsonArray arr = JsonParser.parseString(response.getResponse()).getAsJsonArray();

            if (arr.isEmpty()) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                respond(exchange, 200, "Token doesn't exist anyhow");
            } else {
                JsonObject obj = arr.get(0).getAsJsonObject();

                if (obj.has("token")) {
                    tableManager.deleteRow("authTokens", obj.get("id").getAsInt());
                    respond(exchange, 200, "Token removed");

                } else {
                    tableManager.deleteRow("authTokens", obj.get("id").getAsInt());
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    respond(exchange, 200, "Token removed");
                }

            }

        }
    }

}
