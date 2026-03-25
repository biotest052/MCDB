package com.decacagle.endpoints;

import com.decacagle.DecaDB;
import com.decacagle.data.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.World;

import java.util.logging.Logger;

public class QueryHandler extends APIEndpoint {

    private final TableManager tableManager;
    private final AuthHandler authHandler;

    public QueryHandler(Logger logger, World world, DecaDB plugin, DataWorker worker) {
        super(logger, world, plugin, worker);
        this.tableManager = new TableManager(logger, world, worker);
        this.authHandler = new AuthHandler(logger, world, plugin, worker, tableManager);
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

        query = query.strip();
        if (query.isEmpty()) {
            respond(exchange, 400, "Bad Request: Empty query");
            return;
        }

        if (query.contains("auth register") || query.contains("auth login"))
            logger.info("Received query: " + (query.contains("auth register") ? "auth register *****" : "auth login *****"));
        else
            logger.info("Received query: " + query);

        final String finalQuery = query;
        runSynchronously(() -> parseQuery(exchange, finalQuery));
    }

    public void parseQuery(HttpExchange exchange, String query) {
        String[] args = query.split("\\s+", 7);

        String command = args[0].toUpperCase();

        switch (command) {
            case "SELECT" -> handleSelect(exchange, query, args);
            case "INSERT" -> handleInsert(exchange, query, args);
            case "UPDATE" -> handleUpdate(exchange, query, args);
            case "DELETE" -> handleDelete(exchange, query, args);
            case "CREATE" -> handleCreate(exchange, args);
            case "PROTECT" -> handleProtect(exchange, args);
            case "AUTH"   -> authHandler.parseAuthQuery(exchange, query);
            default -> respond(exchange, 400,
                    "Bad Request: Unknown command '" + args[0] + "'. Supported: SELECT, INSERT, CREATE, UPDATE, DELETE, PROTECT, AUTH");
        }
    }

    private void handleSelect(HttpExchange exchange, String query, String[] args) {
        if (args.length < 4) {
            respond(exchange, 400, "Bad Request: SELECT requires at least 4 tokens: SELECT {* or id} FROM {table}");
            return;
        }
        if (!args[2].equalsIgnoreCase("FROM")) {
            respond(exchange, 400, "Bad Request: Expected FROM after SELECT target");
            return;
        }

        String tableTitle = args[3];
        if (!DataUtilities.isSafeIdentifier(tableTitle)) {
            respond(exchange, 400, "Bad Request: Invalid table name '" + tableTitle + "'");
            return;
        }

        if (args[1].equals("*")) {
            if (args.length >= 6 && args[4].equalsIgnoreCase("WHERE")) {
                String condition = args[5];
                String[] kv = condition.split("=", 2);
                if (kv.length != 2 || kv[0].isEmpty() || kv[1].isEmpty()) {
                    respond(exchange, 400, "Bad Request: WHERE condition must be formatted as key=value");
                    return;
                }
                if (!DataUtilities.isSafeIdentifier(kv[0])) {
                    respond(exchange, 400, "Bad Request: Invalid column name '" + kv[0] + "'");
                    return;
                }
                selectAllWhere(exchange, tableTitle, kv[0], kv[1]);
            } else {
                selectAll(exchange, tableTitle);
            }
        } else if (isNumeric(args[1])) {
            if (args.length != 4) {
                respond(exchange, 400, "Bad Request: SELECT {id} FROM {table} takes exactly 4 tokens");
                return;
            }
            selectId(exchange, Integer.parseInt(args[1]), tableTitle);
        } else {
            respond(exchange, 400, "Bad Request: SELECT target must be * or a numeric id");
        }
    }

    private void handleInsert(HttpExchange exchange, String query, String[] args) {
        if (args.length < 5) {
            respond(exchange, 400, "Bad Request: INSERT INTO {table} VALUE {json}");
            return;
        }
        if (!args[1].equalsIgnoreCase("INTO")) {
            respond(exchange, 400, "Bad Request: Expected INTO after INSERT");
            return;
        }
        if (!args[3].equalsIgnoreCase("VALUE")) {
            respond(exchange, 400, "Bad Request: Expected VALUE after table name");
            return;
        }

        String tableTitle = args[2];
        if (!DataUtilities.isSafeIdentifier(tableTitle)) {
            respond(exchange, 400, "Bad Request: Invalid table name '" + tableTitle + "'");
            return;
        }

        int valueIndex = indexOfKeyword(query, "VALUE");
        if (valueIndex == -1) {
            respond(exchange, 400, "Bad Request: Could not locate VALUE keyword");
            return;
        }
        String value = query.substring(valueIndex + 5).strip();

        if (!isValidJson(value)) {
            respond(exchange, 400, "Bad Request: INSERT value must be valid JSON");
            return;
        }

        insertInto(exchange, tableTitle, value);
    }

    private void handleUpdate(HttpExchange exchange, String query, String[] args) {
        if (args.length < 6) {
            respond(exchange, 400, "Bad Request: UPDATE {id} IN {table} SET {json}");
            return;
        }
        if (!isNumeric(args[1])) {
            respond(exchange, 400, "Bad Request: UPDATE target must be a numeric id");
            return;
        }
        if (!args[2].equalsIgnoreCase("IN")) {
            respond(exchange, 400, "Bad Request: Expected IN after id");
            return;
        }
        if (!args[4].equalsIgnoreCase("SET")) {
            respond(exchange, 400, "Bad Request: Expected SET after table name");
            return;
        }

        String tableTitle = args[3];
        if (!DataUtilities.isSafeIdentifier(tableTitle)) {
            respond(exchange, 400, "Bad Request: Invalid table name '" + tableTitle + "'");
            return;
        }

        int setIndex = indexOfKeyword(query, "SET");
        if (setIndex == -1) {
            respond(exchange, 400, "Bad Request: Could not locate SET keyword");
            return;
        }
        String rowValue = query.substring(setIndex + 3).strip();

        if (!isValidJson(rowValue)) {
            respond(exchange, 400, "Bad Request: UPDATE value must be valid JSON");
            return;
        }

        updateId(exchange, Integer.parseInt(args[1]), tableTitle, rowValue);
    }

    private void handleDelete(HttpExchange exchange, String query, String[] args) {
        if (args.length < 3) {
            respond(exchange, 400, "Bad Request: DELETE requires at least 3 tokens");
            return;
        }

        if (args[1].equalsIgnoreCase("TABLE")) {
            // DELETE TABLE tableTitle
            if (args.length != 3) {
                respond(exchange, 400, "Bad Request: DELETE TABLE {table}");
                return;
            }
            String tableTitle = args[2];
            if (!DataUtilities.isSafeIdentifier(tableTitle)) {
                respond(exchange, 400, "Bad Request: Invalid table name '" + tableTitle + "'");
                return;
            }
            deleteTable(exchange, tableTitle);

        } else if (args[1].equals("*")) {
            if (!args[2].equalsIgnoreCase("FROM")) {
                respond(exchange, 400, "Bad Request: Expected FROM after *");
                return;
            }
            if (args.length < 4) {
                respond(exchange, 400, "Bad Request: DELETE * FROM {table}");
                return;
            }
            String tableTitle = args[3];
            if (!DataUtilities.isSafeIdentifier(tableTitle)) {
                respond(exchange, 400, "Bad Request: Invalid table name '" + tableTitle + "'");
                return;
            }

            if (args.length >= 6 && args[4].equalsIgnoreCase("WHERE")) {
                String[] kv = args[5].split("=", 2);
                if (kv.length != 2 || kv[0].isEmpty() || kv[1].isEmpty()) {
                    respond(exchange, 400, "Bad Request: WHERE condition must be key=value");
                    return;
                }
                if (!DataUtilities.isSafeIdentifier(kv[0])) {
                    respond(exchange, 400, "Bad Request: Invalid column name '" + kv[0] + "'");
                    return;
                }
                deleteAllWhere(exchange, tableTitle, kv[0], kv[1]);
            } else {
                deleteAll(exchange, tableTitle);
            }

        } else if (isNumeric(args[1])) {
            if (!args[2].equalsIgnoreCase("FROM")) {
                respond(exchange, 400, "Bad Request: Expected FROM after id");
                return;
            }
            if (args.length != 4) {
                respond(exchange, 400, "Bad Request: DELETE {id} FROM {table}");
                return;
            }
            String tableTitle = args[3];
            if (!DataUtilities.isSafeIdentifier(tableTitle)) {
                respond(exchange, 400, "Bad Request: Invalid table name '" + tableTitle + "'");
                return;
            }
            deleteId(exchange, Integer.parseInt(args[1]), tableTitle);

        } else {
            respond(exchange, 400, "Bad Request: DELETE target must be TABLE, *, or a numeric id");
        }
    }

    private void handleCreate(HttpExchange exchange, String[] args) {
        if (args.length != 3 || !args[1].equalsIgnoreCase("TABLE")) {
            respond(exchange, 400, "Bad Request: CREATE TABLE {tableName}");
            return;
        }
        String tableTitle = args[2];
        if (!DataUtilities.isSafeIdentifier(tableTitle)) {
            respond(exchange, 400, "Bad Request: Invalid table name '" + tableTitle + "'");
            return;
        }
        createTable(exchange, tableTitle);
    }

    private void handleProtect(HttpExchange exchange, String[] args) {
        if (args.length != 3) {
            respond(exchange, 400, "Bad Request: PROTECT {table} {flags|REMOVE}. Flags: c r u d *");
            return;
        }
        String tableTitle = args[1];
        if (!DataUtilities.isSafeIdentifier(tableTitle)) {
            respond(exchange, 400, "Bad Request: Invalid table name '" + tableTitle + "'");
            return;
        }

        if (args[2].equalsIgnoreCase("REMOVE")) {
            protectRemove(exchange, tableTitle);
        } else {
            protect(exchange, tableTitle, args[2]);
        }
    }

    public void selectAll(HttpExchange exchange, String tableTitle) {
        ProtectionCheckResponse pCheck = checkProtected(exchange, tableTitle, 'r');
        if (pCheck.hadError()) return;

        MethodResponse response;
        if (pCheck.isProtected() && !pCheck.isAdmin()) {
            response = tableManager.readTableWithCondition(tableTitle, "userId", "" + pCheck.getUserId());
        } else {
            response = tableManager.readTable(tableTitle);
        }

        if (response.hasError()) {
            respond(exchange, response.getStatusCode(), response.getStatusMessage());
        } else {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            respond(exchange, response.getStatusCode(), response.getResponse());
        }
    }

    public void selectAllWhere(HttpExchange exchange, String tableTitle, String key, String target) {
        ProtectionCheckResponse pCheck = checkProtected(exchange, tableTitle, 'r');
        if (pCheck.hadError()) return;

        if (pCheck.isProtected() && !pCheck.isAdmin()) {
            int userId = pCheck.getUserId();
            MethodResponse response = tableManager.readTableWithCondition(tableTitle, key, target);
            if (response.hasError()) {
                respond(exchange, response.getStatusCode(), response.getStatusMessage());
                return;
            }
            MethodResponse filterAttempt = DataUtilities.filterJsonArray(response.getResponse(), "userId", "" + userId);
            if (filterAttempt.hasError()) {
                respond(exchange, filterAttempt.getStatusCode(), filterAttempt.getStatusMessage());
            } else {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                respond(exchange, filterAttempt.getStatusCode(), filterAttempt.getResponse());
            }
        } else {
            MethodResponse response = tableManager.readTableWithCondition(tableTitle, key, target);
            if (response.hasError()) {
                respond(exchange, response.getStatusCode(), response.getStatusMessage());
            } else {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                respond(exchange, response.getStatusCode(), response.getResponse());
            }
        }
    }

    public void selectId(HttpExchange exchange, int rowId, String tableTitle) {
        ProtectionCheckResponse pCheck = checkProtected(exchange, tableTitle, 'r');
        if (pCheck.hadError()) return;

        MethodResponse response = tableManager.readRow(tableTitle, rowId);
        if (response.hasError()) {
            respond(exchange, response.getStatusCode(), response.getStatusMessage());
            return;
        }

        if (pCheck.isProtected() && !pCheck.isAdmin()) {
            if (!DataUtilities.meetsCondition(response.getResponse(), "userId", "" + pCheck.getUserId())) {
                respond(exchange, 401, "Access Denied: Not Authorized");
                return;
            }
        }

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        respond(exchange, response.getStatusCode(), response.getResponse());
    }

    public void createTable(HttpExchange exchange, String tableTitle) {
        MethodResponse response = tableManager.createTable(tableTitle);
        respond(exchange, response.getStatusCode(),
                response.hasError() ? response.getStatusMessage() : response.getResponse());
    }

    public void insertInto(HttpExchange exchange, String tableTitle, String value) {
        ProtectionCheckResponse pCheck = checkProtected(exchange, tableTitle, 'c');
        if (pCheck.hadError()) return;

        String rowValue = value;
        if (pCheck.isProtected() && !pCheck.isAdmin()) {
            rowValue = DataUtilities.addValueToJSON(pCheck.getUserId(), "userId", value);
        }

        MethodResponse response = tableManager.insertRow(tableTitle, rowValue);
        respond(exchange, response.getStatusCode(),
                response.hasError() ? response.getStatusMessage() : response.getResponse());
    }

    public void deleteAll(HttpExchange exchange, String tableTitle) {
        ProtectionCheckResponse pCheck = checkProtected(exchange, tableTitle, 'd');
        if (pCheck.hadError()) return;

        MethodResponse response;
        if (pCheck.isProtected() && !pCheck.isAdmin()) {
            response = tableManager.deleteAllFromTableWithCondition(tableTitle, "userId", "" + pCheck.getUserId());
        } else {
            response = tableManager.deleteAllFromTable(tableTitle);
        }
        respond(exchange, response.getStatusCode(),
                response.hasError() ? response.getStatusMessage() : response.getResponse());
    }

    public void deleteAllWhere(HttpExchange exchange, String tableTitle, String key, String target) {
        ProtectionCheckResponse pCheck = checkProtected(exchange, tableTitle, 'd');
        if (pCheck.hadError()) return;

        MethodResponse response;
        if (pCheck.isProtected() && !pCheck.isAdmin()) {
            response = tableManager.deleteAllFromTableWithCondition(tableTitle, "userId", "" + pCheck.getUserId());
        } else {
            response = tableManager.deleteAllFromTableWithCondition(tableTitle, key, target);
        }
        respond(exchange, response.getStatusCode(),
                response.hasError() ? response.getStatusMessage() : response.getResponse());
    }

    public void deleteId(HttpExchange exchange, int rowId, String tableTitle) {
        ProtectionCheckResponse pCheck = checkProtected(exchange, tableTitle, 'd');
        if (pCheck.hadError()) return;

        if (pCheck.isProtected() && !pCheck.isAdmin()) {
            MethodResponse readAttempt = tableManager.readRow(tableTitle, rowId);
            if (readAttempt.hasError()) {
                respond(exchange, readAttempt.getStatusCode(), readAttempt.getStatusMessage());
                return;
            }
            if (!DataUtilities.meetsCondition(readAttempt.getResponse(), "userId", "" + pCheck.getUserId())) {
                respond(exchange, 401, "Access Denied: Not Authorized");
                return;
            }
        }

        MethodResponse response = tableManager.deleteRow(tableTitle, rowId);
        respond(exchange, response.getStatusCode(),
                response.hasError() ? response.getStatusMessage() : response.getResponse());
    }

    public void deleteTable(HttpExchange exchange, String tableTitle) {
        MethodResponse response = tableManager.deleteTable(tableTitle);
        respond(exchange, response.getStatusCode(),
                response.hasError() ? response.getStatusMessage() : response.getResponse());
    }

    public void updateId(HttpExchange exchange, int rowId, String tableTitle, String rowValue) {
        ProtectionCheckResponse pCheck = checkProtected(exchange, tableTitle, 'u');
        if (pCheck.hadError()) return;

        if (pCheck.isProtected() && !pCheck.isAdmin()) {
            MethodResponse readAttempt = tableManager.readRow(tableTitle, rowId);
            if (readAttempt.hasError()) {
                respond(exchange, readAttempt.getStatusCode(), readAttempt.getStatusMessage());
                return;
            }
            if (!DataUtilities.meetsCondition(readAttempt.getResponse(), "userId", "" + pCheck.getUserId())) {
                respond(exchange, 401, "Access Denied: Not Authorized");
                return;
            }
        }

        MethodResponse response = tableManager.updateRow(tableTitle, rowId, rowValue);
        respond(exchange, response.getStatusCode(),
                response.hasError() ? response.getStatusMessage() : response.getResponse());
    }

    public void protect(HttpExchange exchange, String tableTitle, String flags) {
        MethodResponse response = tableManager.protectTable(tableTitle, flags);
        respond(exchange, response.getStatusCode(),
                response.hasError() ? response.getStatusMessage() : response.getResponse());
    }

    public void protectRemove(HttpExchange exchange, String tableTitle) {
        MethodResponse response = tableManager.removeProtections(tableTitle);
        respond(exchange, response.getStatusCode(),
                response.hasError() ? response.getStatusMessage() : response.getResponse());
    }

    public String getAuthTokenFromRequest(HttpExchange exchange) {
        return exchange.getRequestHeaders().getFirst("Authorization");
    }

    public int getUserIdFromAuthToken(String authToken) {
        MethodResponse response = tableManager.readTableWithCondition("authTokens", "token", authToken);
        if (response.hasError()) {
            logger.info(response.getStatusMessage());
            return 0;
        }

        JsonArray arr = JsonParser.parseString(response.getResponse()).getAsJsonArray();
        if (arr.isEmpty()) return 0;

        JsonObject obj = arr.get(0).getAsJsonObject();

        if (!obj.has("expiration")) {
            tableManager.deleteRow("authTokens", obj.get("id").getAsInt());
            return 0;
        }

        if (DataUtilities.isExpired(obj.get("expiration").getAsString())) {
            tableManager.deleteRow("authTokens", obj.get("id").getAsInt());
            return 0;
        }

        return obj.get("userId").getAsInt();
    }

    public ProtectionCheckResponse checkProtected(HttpExchange exchange, String tableTitle, char flag) {
        MethodResponse checkProtection = tableManager.getProtectionFlags(tableTitle);
        if (checkProtection.hasError()) {
            respond(exchange, checkProtection.getStatusCode(), checkProtection.getStatusMessage());
            return new ProtectionCheckResponse(true, 0, false, true);
        }

        String flags = checkProtection.getResponse();
        if (flags.indexOf(flag) == -1 && flags.indexOf('*') == -1) {
            return new ProtectionCheckResponse(false, 0, false, false);
        }

        String authToken = getAuthTokenFromRequest(exchange);
        if (authToken == null) {
            respond(exchange, 401, "Access Denied: No Authorization header provided");
            return new ProtectionCheckResponse(true, 0, false, true);
        }

        int userId = getUserIdFromAuthToken(authToken);
        if (userId == 0) {
            respond(exchange, 401, "Access Denied: Invalid or expired token");
            return new ProtectionCheckResponse(true, 0, false, true);
        }

        return new ProtectionCheckResponse(true, userId, false, false);
    }

    private int indexOfKeyword(String query, String keyword) {
        String upper = query.toUpperCase();
        int idx = upper.indexOf(keyword.toUpperCase());
        return idx;
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidJson(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            JsonParser.parseString(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}