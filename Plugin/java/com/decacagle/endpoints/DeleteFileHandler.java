package com.decacagle.endpoints;

import com.decacagle.DecaDB;
import com.decacagle.data.DataUtilities;
import com.decacagle.data.DataWorker;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.logging.Logger;

public class DeleteFileHandler extends APIEndpoint {

    private final HttpServer server;
    private final int indexOffset = -1;

    public DeleteFileHandler(HttpServer server, Logger logger, World world, DecaDB plugin, DataWorker worker) {
        super(logger, world, plugin, worker);
        this.server = server;
    }

    @Override
    public void handle(HttpExchange exchange) {
        addCorsHeaders(exchange);
        if (preflightCheck(exchange)) return;

        String rawQuery = exchange.getRequestURI().getQuery();
        if (rawQuery == null || !rawQuery.startsWith("i=")) {
            respond(exchange, 400, "Bad Request: Missing required query parameter 'i' (e.g. /deleteFile?i=3)");
            return;
        }

        int index;
        try {
            index = Integer.parseInt(rawQuery.substring(2));
        } catch (NumberFormatException e) {
            respond(exchange, 400, "Bad Request: Parameter 'i' must be a positive integer");
            return;
        }

        if (index <= 0) {
            respond(exchange, 400, "Bad Request: File index must be a positive integer");
            return;
        }

        final int finalIndex = index;
        runSynchronously(() -> deleteFile(server, exchange, finalIndex));
    }

    public void deleteFile(HttpServer server, HttpExchange exchange, int index) {
        String metadata = worker.readChunk(0, -index + indexOffset, false, 1);

        if (!DataUtilities.isValidFileMetadata(metadata)) {
            respond(exchange, 404, "Not Found: File doesn't exist or has corrupted metadata");
            return;
        }

        String targetTitle = DataUtilities.parseTitle(metadata);
        int lastIndex = DataUtilities.parseLastIndexTable(metadata);
        int nextIndex = DataUtilities.parseNextIndexTable(metadata);

        logger.info("Deleting file: " + targetTitle + " (lastIndex=" + lastIndex + ", nextIndex=" + nextIndex + ")");

        if (lastIndex == 0) {
            worker.writeToChunk("" + nextIndex, 0, -1, false, 1);
        } else {
            String lastMeta = worker.readChunk(0, -lastIndex + indexOffset, false, 1);
            String lastTitle = DataUtilities.parseTitle(lastMeta);
            String lastMime = DataUtilities.parseFileMime(lastMeta);
            int lastLast = DataUtilities.parseLastIndexTable(lastMeta);

            String newMeta = DataUtilities.fileMetadataBuilder(lastTitle, lastMime, lastLast, nextIndex);
            worker.deleteChunk(0, -lastIndex + indexOffset, false, 1);
            worker.writeToChunk(newMeta, 0, -lastIndex + indexOffset, false, 1);
        }

        if (nextIndex != 0) {
            String nextMeta = worker.readChunk(0, -nextIndex + indexOffset, false, 1);
            String nextTitle = DataUtilities.parseTitle(nextMeta);
            String nextMime = DataUtilities.parseFileMime(nextMeta);
            int nextNext = DataUtilities.parseNextIndexTable(nextMeta);

            String newMeta = DataUtilities.fileMetadataBuilder(nextTitle, nextMime, lastIndex, nextNext);
            worker.deleteChunk(0, -nextIndex + indexOffset, false, 1);
            worker.writeToChunk(newMeta, 0, -nextIndex + indexOffset, false, 1);
        }

        worker.deleteChunk(0, -index + indexOffset, false, 1);
        worker.deleteChunk(1, -index + indexOffset, true, 1);

        deleteSign(index);

        try {
            server.removeContext(DataUtilities.contextNameBuilder(targetTitle));
        } catch (IllegalArgumentException e) {
            logger.warning("Could not remove server context for '" + targetTitle + "': " + e.getMessage());
        }

        respond(exchange, 200, "Deleted file with success: " + targetTitle);
    }

    public void deleteSign(int fileIndex) {
        world.getBlockAt(-1, -63, -(fileIndex * 16) + (indexOffset * 16) - 1).setType(Material.AIR);
    }
}