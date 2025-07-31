package com.decacagle.endpoints;

import com.decacagle.DecaDB;
import com.decacagle.data.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.World;

import javax.xml.crypto.Data;
import java.util.logging.Logger;

public class QueryHandler extends APIEndpoint {

    private TableManager tableManager;
    private AuthHandler authHandler;

    public QueryHandler(Logger logger, World world, DecaDB plugin, DataWorker worker) {
        super(logger, world, plugin, worker);

        this.tableManager = new TableManager(logger, world, worker);

        this.authHandler = new AuthHandler(logger, world, plugin, worker, tableManager);

    }

    public void handle(HttpExchange exchange) {
        addCorsHeaders(exchange);

        if (!preflightCheck(exchange)) {

            String query = parseExchangeBody(exchange);

            logger.info("received query: " + query);

            runSynchronously(() -> parseQuery(exchange, query));

        }

    }

    public void parseQuery(HttpExchange exchange, String query) {

        String[] args = query.split(" ");

        if (args[0].equalsIgnoreCase("select")) {
            if (args.length >= 4) {
                if (args[1].equalsIgnoreCase("*")) {
                    if (args[2].equalsIgnoreCase("from")) {
                        String tableTitle = args[3];
                        // TODO: check for admin key, if not admin return 401
                        // if (tableTitle.equalsIgnoreCase("users") || tableTitle.equalsIgnoreCase("authTokens")) {
                        //     respond(exchange, 401, "Access Denied: Cannot request queries on table " + tableTitle);
                        // } else {
                        if (args.length >= 6) {
                            if (args[4].equalsIgnoreCase("where")) {

                                selectAllWhere(exchange, tableTitle, args);

                            } else {
                                respond(exchange, 400, "Bad Request: Improper query, conditional select queries should be formatted as SELECT * FROM {tableTitle} WHERE {keyName}={targetValue}");
                            }
                        } else {
                            // request all data from table tableTitle

                            selectAll(exchange, tableTitle, args);

                        }
                        // }
                    } else {
                        respond(exchange, 400, "Bad Request: Improper query, select all queries should be formatted as SELECT * FROM {tableTitle}");
                    }
                } else if (isNumeric(args[1])) {
                    if (args[2].equalsIgnoreCase("from")) {
                        if (args.length == 4) {

                            selectId(exchange, args);

                        } else {
                            respond(exchange, 400, "Bad Request: Improper query, select queries should be formatted as SELECT {* or id} FROM {tableTitle}");
                        }
                    } else {
                        respond(exchange, 400, "Bad Request: Improper query, select queries should be formatted as SELECT {* or id} FROM {tableTitle}");
                    }

                } else {
                    respond(exchange, 400, "Bad Request: Improper query, select queries should be formatted as SELECT {* or id} FROM {tableTitle}");
                }
            } else {
                respond(exchange, 400, "Bad Request: Improper query, select queries should be formatted as SELECT {* or id} FROM {tableTitle}");
            }
        } else if (args[0].equalsIgnoreCase("create")) {
            if (args.length == 3) {
                if (args[1].equalsIgnoreCase("table")) {

                    createTable(exchange, args);

                } else {
                    respond(exchange, 400, "Bad Request: Improper query, create queries should be formatted as CREATE TABLE {tableTitle}");
                }
            } else {
                respond(exchange, 400, "Bad Request: Improper query, create queries should be formatted as CREATE TABLE {tableTitle}");
            }
        } else if (args[0].equalsIgnoreCase("insert")) {
            if (args.length >= 5) {
                if (args[1].equalsIgnoreCase("into")) {
                    if (args[3].equalsIgnoreCase("value")) {

                        insertInto(exchange, query, args);

                    } else {
                        respond(exchange, 400, "Bad Request: Improper query, insert queries should be formatted as INSERT INTO {tableTitle} VALUE {rowValue}");
                    }
                } else {
                    respond(exchange, 400, "Bad Request: Improper query, insert queries should be formatted as INSERT INTO {tableTitle} VALUE {rowValue}");
                }
            } else {
                respond(exchange, 400, "Bad Request: Improper query, insert queries should be formatted as INSERT INTO {tableTitle} VALUE {rowValue}");
            }
        } else if (args[0].equalsIgnoreCase("delete")) {
            if (args[1].equalsIgnoreCase("*")) {
                if (args.length == 4) {
                    if (args[2].equalsIgnoreCase("from")) {

                        deleteAll(exchange, args);

                    } else {
                        respond(exchange, 400, "Bad Request: Improper query, delete all queries should be formatted as DELETE * FROM {tableTitle}");
                    }
                } else {
                    respond(exchange, 400, "Bad Request: Improper query, delete queries should be formatted as DELETE {* or id} FROM {tableTitle}");
                }
            } else if (isNumeric(args[1])) {
                if (args.length == 4) {
                    if (args[2].equalsIgnoreCase("from")) {

                        deleteId(exchange, args);

                    } else {
                        respond(exchange, 400, "Bad Request: Improper query, delete queries should be formatted as DELETE {* or id} FROM {tableTitle}");
                    }

                } else {
                    respond(exchange, 400, "Bad Request: Improper query, delete queries should be formatted as DELETE {* or id} FROM {tableTitle}");
                }
            } else if (args[1].equalsIgnoreCase("table")) {
                if (args.length == 3) {

                    deleteTable(exchange, args);

                } else {
                    respond(exchange, 400, "Bad Request: Improper query, delete queries should be formatted as DELETE {* or id} FROM {tableTitle}");
                }
            } else {
                respond(exchange, 400, "Bad Request: Improper query, delete queries should be formatted as DELETE {* or id} FROM {tableTitle}");
            }

        } else if (args[0].equalsIgnoreCase("update")) {
            if (args.length >= 6) {
                if (isNumeric(args[1])) {
                    if (args[2].equalsIgnoreCase("in")) {
                        if (args[4].equalsIgnoreCase("set")) {

                            updateId(exchange, query, args);

                        } else {
                            respond(exchange, 400, "Bad Request: Improper query, update queries should be formatted as UPDATE {id} IN {tableTitle} SET {rowValue}");
                        }
                    } else {
                        respond(exchange, 400, "Bad Request: Improper query, update queries should be formatted as UPDATE {id} IN {tableTitle} SET {rowValue}");
                    }
                } else {
                    respond(exchange, 400, "Bad Request: Improper query, update queries should be formatted as UPDATE {id} IN {tableTitle} SET {rowValue}");
                }
            } else {
                respond(exchange, 400, "Bad Request: Improper query, update queries should be formatted as UPDATE {id} IN {tableTitle} SET {rowValue}");
            }
        } else if (args[0].equalsIgnoreCase("raw")) {
            // TODO: Check for admin key, if not admin return 401
            if (args.length >= 4) {
                if (isNumeric(args[2]) && isNumeric(args[3])) {
                    if (args[1].equalsIgnoreCase("read")) {

                        rawRead(exchange, args);

                    } else if (args[1].equalsIgnoreCase("delete")) {

                        rawDelete(exchange, args);

                    } else if (args[1].equalsIgnoreCase("write")) {
                        if (args.length >= 5) {

                            rawWrite(exchange, query, args);

                        } else
                            respond(exchange, 400, "Bad request: Improper query, raw write queries should be formatted as RAW WRITE {X} {Z} {content}");
                    } else {
                        respond(exchange, 400, "Bad request: Improper query, raw queries should be formatted as RAW {READ or DELETE} {X} {Z}");
                    }
                }
            } else
                respond(exchange, 400, "Bad request: Improper query, raw queries should be formatted as RAW {READ or DELETE} {X} {Z}");
        } else if (args[0].equalsIgnoreCase("protect")) {
            // TODO: check for admin key, if not admin return 401: Access Denied

            if (args.length == 3) {
                if (args[2].equalsIgnoreCase("remove")) {
                    protectRemove(exchange, args);
                } else {
                    protect(exchange, args);
                }

            } else {
                respond(exchange, 400, "Bad request: Improper query, protect queries should be formatted as PROTECT {tableTitle} {protectionFlags}. Example: PROTECT profiles cud - add Create, Update and Delete protection to table profiles");
            }

        } else if (args[0].equalsIgnoreCase("auth")) {
            authHandler.parseAuthQuery(exchange, query);
        } else {
            respond(exchange, 400, "Bad Request: Action words supported are SELECT, INSERT, CREATE, UPDATE, DELETE, PROTECT and AUTH");
        }

    }

    public void selectAll(HttpExchange exchange, String tableTitle, String[] args) {
        ProtectionCheckResponse protectionCheck = checkProtected(exchange, tableTitle, 'r');

        // Check if protection check resulted in response to request. If so, do not continue
        if (!protectionCheck.hadError()) {
            if (protectionCheck.isProtected() && !protectionCheck.isAdmin()) {
                // Table is protected and requester is authenticated
                int userId = protectionCheck.getUserId();
                MethodResponse response = tableManager.readTableWithCondition(tableTitle, "userId", "" + userId);
                if (response.hasError()) {
                    respond(exchange, response.getStatusCode(), response.getStatusMessage());
                } else {
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    respond(exchange, response.getStatusCode(), response.getResponse());
                }

            } else {
                // Table is NOT protected OR requester has admin key

                MethodResponse response = tableManager.readTable(tableTitle);
                if (response.hasError()) {
                    respond(exchange, response.getStatusCode(), response.getStatusMessage());
                } else {
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    respond(exchange, response.getStatusCode(), response.getResponse());
                }

            }
        }
    }

    public void selectAllWhere(HttpExchange exchange, String tableTitle, String[] args) {
        ProtectionCheckResponse protectionCheck = checkProtected(exchange, tableTitle, 'r');

        // Check if protection check resulted in response to request. If so, do not continue
        if (!protectionCheck.hadError()) {
            if (protectionCheck.isProtected() && !protectionCheck.isAdmin()) {
                // Table is protected and requester is authenticated

                int userId = protectionCheck.getUserId();
                String[] condition = args[5].split("=");
                if (condition.length == 2) {
                    String key = condition[0];
                    String target = condition[1];
                    MethodResponse response = tableManager.readTableWithCondition(tableTitle, key, target);

                    if (response.hasError()) {
                        respond(exchange, response.getStatusCode(), response.getStatusMessage());
                    } else {

                        MethodResponse filterAttempt = DataUtilities.filterJsonArray(response.getResponse(), "userId", "" + userId);

                        if (filterAttempt.hasError()) {
                            respond(exchange, response.getStatusCode(), response.getStatusMessage());
                        } else {
                            exchange.getResponseHeaders().add("Content-Type", "application/json");
                            respond(exchange, filterAttempt.getStatusCode(), filterAttempt.getResponse());
                        }
                    }

                } else {
                    respond(exchange, 400, "Bad Request: Improper query, conditional select queries should be formatted as SELECT * FROM {tableTitle} WHERE {keyName}={targetValue}");
                }

            } else {
                // Table is NOT protected OR requester has admin key

                String[] condition = args[5].split("=");
                if (condition.length == 2) {
                    String key = condition[0];
                    String target = condition[1];
                    MethodResponse response = tableManager.readTableWithCondition(tableTitle, key, target);

                    if (response.hasError()) {
                        respond(exchange, response.getStatusCode(), response.getStatusMessage());
                    } else {
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        respond(exchange, response.getStatusCode(), response.getResponse());
                    }

                } else {
                    respond(exchange, 400, "Bad Request: Improper query, conditional select queries should be formatted as SELECT * FROM {tableTitle} WHERE {keyName}={targetValue}");
                }

            }
        }

    }

    public void selectId(HttpExchange exchange, String[] args) {
        int rowId = Integer.parseInt(args[1]);
        String tableTitle = args[3];

        ProtectionCheckResponse protectionCheck = checkProtected(exchange, tableTitle, 'r');

        // Check if protection check resulted in response to request. If so, do not continue
        if (!protectionCheck.hadError()) {
            if (protectionCheck.isProtected() && !protectionCheck.isAdmin()) {
                // Table is protected and requester is authenticated

                int userId = protectionCheck.getUserId();
                MethodResponse response = tableManager.readRow(tableTitle, rowId);

                if (response.hasError()) {
                    respond(exchange, response.getStatusCode(), response.getStatusMessage());
                } else {
                    if (DataUtilities.meetsCondition(response.getResponse(), "userId", "" + userId)) {
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        respond(exchange, response.getStatusCode(), response.getResponse());
                    } else {
                        respond(exchange, 401, "Access Denied: Not Authorized");
                    }
                }

            } else {
                // Table is NOT protected OR requester has admin key

                MethodResponse response = tableManager.readRow(tableTitle, rowId);

                if (response.hasError()) {
                    respond(exchange, response.getStatusCode(), response.getStatusMessage());
                } else {
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    respond(exchange, response.getStatusCode(), response.getResponse());
                }

            }
        }
    }

    public void createTable(HttpExchange exchange, String[] args) {
// TODO: Check for admin key, return 401 if not admin
        String tableTitle = args[2];
        // create table by the name of tableTitle
        MethodResponse response = tableManager.createTable(tableTitle);

        if (response.hasError()) {
            respond(exchange, response.getStatusCode(), response.getStatusMessage());
        } else {
            respond(exchange, response.getStatusCode(), response.getResponse());
        }
    }

    public void insertInto(HttpExchange exchange, String query, String[] args) {
        String tableTitle = args[2];
        String value = query.substring(query.toLowerCase().indexOf("value") + 6);

        ProtectionCheckResponse protectionCheck = checkProtected(exchange, tableTitle, 'c');

        // Check if protection check resulted in response to request. If so, do not continue
        if (!protectionCheck.hadError()) {
            if (protectionCheck.isProtected() && !protectionCheck.isAdmin()) {
                // Table is protected and requester is authenticated

                int userId = protectionCheck.getUserId();
                String rowValue = DataUtilities.addValueToJSON(userId, "userId", value);
                // into row of value rowValue into table tableTitle
                MethodResponse response = tableManager.insertRow(tableTitle, rowValue);

                if (response.hasError()) {
                    respond(exchange, response.getStatusCode(), response.getStatusMessage());
                } else {
                    respond(exchange, response.getStatusCode(), response.getResponse());
                }

            } else {
                // Table is NOT protected OR requester has admin key

                MethodResponse response = tableManager.insertRow(tableTitle, value);

                if (response.hasError()) {
                    respond(exchange, response.getStatusCode(), response.getStatusMessage());
                } else {
                    respond(exchange, response.getStatusCode(), response.getResponse());
                }

            }
        }

    }

    public void deleteAll(HttpExchange exchange, String[] args) {
        String tableTitle = args[3];

        ProtectionCheckResponse protectionCheck = checkProtected(exchange, tableTitle, 'c');

        // Check if protection check resulted in response to request. If so, do not continue
        if (!protectionCheck.hadError()) {
            if (protectionCheck.isProtected() && !protectionCheck.isAdmin()) {
                // Table is protected and requester is authenticated

                int userId = protectionCheck.getUserId();
                MethodResponse response = tableManager.deleteAllFromTableWithCondition(tableTitle, "userId", "" + userId);

                if (response.hasError()) {
                    respond(exchange, response.getStatusCode(), response.getStatusMessage());
                } else {
                    respond(exchange, response.getStatusCode(), response.getResponse());
                }

            } else {
                // Table is NOT protected OR requester has admin key

                MethodResponse response = tableManager.deleteAllFromTable(tableTitle);

                if (response.hasError()) {
                    respond(exchange, response.getStatusCode(), response.getStatusMessage());
                } else {
                    respond(exchange, response.getStatusCode(), response.getResponse());
                }

            }
        }
    }

    public void deleteId(HttpExchange exchange, String[] args) {
// TODO: Check for protection, if protected and auth not valid return 401: Access Denied
        int rowId = Integer.parseInt(args[1]);
        String tableTitle = args[3];

        ProtectionCheckResponse protectionCheck = checkProtected(exchange, tableTitle, 'c');

        // Check if protection check resulted in response to request. If so, do not continue
        if (!protectionCheck.hadError()) {
            if (protectionCheck.isProtected() && !protectionCheck.isAdmin()) {
                // Table is protected and requester is authenticated

                int userId = protectionCheck.getUserId();
                MethodResponse readAttempt = tableManager.readRow(tableTitle, rowId);

                if (readAttempt.hasError()) {
                    respond(exchange, readAttempt.getStatusCode(), readAttempt.getStatusMessage());
                } else {
                    if (DataUtilities.meetsCondition(readAttempt.getResponse(), "userId", "" + userId)) {
                        MethodResponse response = tableManager.deleteRow(tableTitle, rowId);

                        if (response.hasError()) {
                            respond(exchange, response.getStatusCode(), response.getStatusMessage());
                        } else {
                            respond(exchange, response.getStatusCode(), response.getResponse());
                        }
                    } else {
                        respond(exchange, 401, "Access Denied: Not Authorized");
                    }
                }

            } else {
                // Table is NOT protected OR requester has admin key

                MethodResponse response = tableManager.deleteRow(tableTitle, rowId);

                if (response.hasError()) {
                    respond(exchange, response.getStatusCode(), response.getStatusMessage());
                } else {
                    respond(exchange, response.getStatusCode(), response.getResponse());
                }

            }
        }
    }

    public void deleteTable(HttpExchange exchange, String[] args) {
// TODO: Check for admin key, return 401 if not admin
        String tableTitle = args[2];

        MethodResponse response = tableManager.deleteTable(tableTitle);

        if (response.hasError()) {
            respond(exchange, response.getStatusCode(), response.getStatusMessage());
        } else {
            respond(exchange, response.getStatusCode(), response.getResponse());
        }
    }

    public void updateId(HttpExchange exchange, String query, String[] args) {
        int rowId = Integer.parseInt(args[1]);
        String tableTitle = args[3];
        String rowValue = query.substring(query.toLowerCase().indexOf("set") + 4);

        ProtectionCheckResponse protectionCheck = checkProtected(exchange, tableTitle, 'c');

        // Check if protection check resulted in response to request. If so, do not continue
        if (!protectionCheck.hadError()) {
            if (protectionCheck.isProtected() && !protectionCheck.isAdmin()) {
                // Table is protected and requester is authenticated

                int userId = protectionCheck.getUserId();
                MethodResponse response = tableManager.readRow(tableTitle, rowId);

                if (response.hasError()) {
                    respond(exchange, response.getStatusCode(), response.getStatusMessage());
                } else {
                    if (DataUtilities.meetsCondition(response.getResponse(), "userId", "" + userId)) {

                        MethodResponse updateAttempt = tableManager.updateRow(tableTitle, rowId, rowValue);

                        if (updateAttempt.hasError()) {
                            respond(exchange, updateAttempt.getStatusCode(), updateAttempt.getStatusMessage());
                        } else {
                            respond(exchange, updateAttempt.getStatusCode(), updateAttempt.getResponse());
                        }

                    } else {
                        respond(exchange, 401, "Access Denied: Not Authorized");
                    }
                }

            } else {
                // Table is NOT protected OR requester has admin key

                MethodResponse updateAttempt = tableManager.updateRow(tableTitle, rowId, rowValue);

                if (updateAttempt.hasError()) {
                    respond(exchange, updateAttempt.getStatusCode(), updateAttempt.getStatusMessage());
                } else {
                    respond(exchange, updateAttempt.getStatusCode(), updateAttempt.getResponse());
                }

            }
        }
    }

    public void rawRead(HttpExchange exchange, String[] args) {
        int x = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);

        String rawRead = worker.readChunk(x, z, false, 1);

        respond(exchange, 200, "Result: " + rawRead);
    }

    public void rawDelete(HttpExchange exchange, String[] args) {
        int x = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);

        worker.deleteChunk(x, z, false, 1);

        respond(exchange, 200, "Success!");
    }

    public void rawWrite(HttpExchange exchange, String query, String[] args) {
        int x = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);
        String body = query.substring(query.indexOf(args[3]) + args[3].length() + 1);

        worker.deleteChunk(x, z, false, 1);
        worker.writeToChunk(body, x, z, false, 1);

        respond(exchange, 200, "Success!");
    }

    public void protect(HttpExchange exchange, String[] args) {
        String tableTitle = args[1];
        String protectionFlags = args[2];

        MethodResponse protectAttempt = tableManager.protectTable(tableTitle, protectionFlags);

        if (protectAttempt.hasError()) {
            respond(exchange, protectAttempt.getStatusCode(), protectAttempt.getStatusMessage());
        } else {
            respond(exchange, protectAttempt.getStatusCode(), protectAttempt.getResponse());
        }
    }

    public void protectRemove(HttpExchange exchange, String[] args) {
        String tableTitle = args[1];

        MethodResponse protectRemoveAttempt = tableManager.removeProtections(tableTitle);

        if (protectRemoveAttempt.hasError()) {
            respond(exchange, protectRemoveAttempt.getStatusCode(), protectRemoveAttempt.getStatusMessage());
        } else {
            respond(exchange, protectRemoveAttempt.getStatusCode(), protectRemoveAttempt.getResponse());
        }
    }

    public String getAuthTokenFromRequest(HttpExchange exchange) {
        return exchange.getRequestHeaders().getFirst("Authorization");
    }

    /**
     * Checks whether the given authToken exists and is not expired, then returns associated userId
     * If the authToken is expired, it will be deleted.
     * If the authToken is expired or doesn't exist, getUserIdFromAuthToken returns 0;
     *
     * @param authToken The auth token believed to be associated with a user
     */
    public int getUserIdFromAuthToken(String authToken) {
        MethodResponse response = tableManager.readTableWithCondition("authTokens", "token", authToken);

        if (response.hasError()) {
            logger.info(response.getStatusMessage());
            return 0;
        } else {

            JsonArray arr = JsonParser.parseString(response.getResponse()).getAsJsonArray();

            if (arr.isEmpty()) {
                return 0;
            } else {
                JsonObject obj = arr.get(0).getAsJsonObject();

                if (obj.has("expiration")) {
                    String expiration = obj.get("expiration").getAsString();

                    boolean isExpired = DataUtilities.isExpired(expiration);

                    if (isExpired) {
                        tableManager.deleteRow("authTokens", obj.get("id").getAsInt());
                        return 0;

                    } else {
                        return obj.get("userId").getAsInt();
                    }

                } else {
                    tableManager.deleteRow("authTokens", obj.get("id").getAsInt());
                    return 0;
                }

            }

        }

    }

    public ProtectionCheckResponse checkProtected(HttpExchange exchange, String tableTitle, char flag) {
        MethodResponse checkProtection = tableManager.getProtectionFlags(tableTitle);
        if (checkProtection.hasError()) {
            respond(exchange, checkProtection.getStatusCode(), checkProtection.getStatusMessage());
            return new ProtectionCheckResponse(true, 0, false, true);
        } else {
            String flags = checkProtection.getResponse();
            if (flags.indexOf(flag) != -1 || flags.indexOf('*') != -1) {
                String authToken = getAuthTokenFromRequest(exchange);
                if (authToken == null) {
                    respond(exchange, 401, "Access Denied: No authorization provided");
                    return new ProtectionCheckResponse(true, 0, false, true);
                } else {
                    int associatedUserId = getUserIdFromAuthToken(authToken);
                    if (associatedUserId == 0) {
                        respond(exchange, 401, "Access Denied: Invalid authorization");
                        return new ProtectionCheckResponse(true, 0, false, true);
                    } else {
                        return new ProtectionCheckResponse(true, associatedUserId, false, false);
                    }
                }
            } else {
                return new ProtectionCheckResponse(false, 0, false, false);
            }
        }
    }

    public boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

}
