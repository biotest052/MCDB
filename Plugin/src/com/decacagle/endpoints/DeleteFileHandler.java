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

    private HttpServer server;
    private int indexOffset = -1;

    public DeleteFileHandler(HttpServer server, Logger logger, World world, DecaDB plugin, DataWorker worker) {
        super(logger, world, plugin, worker);

        this.server = server;

    }

    public void handle(HttpExchange exchange) {
        addCorsHeaders(exchange);

        if (!preflightCheck(exchange)) {

            int index = Integer.parseInt(exchange.getRequestURI().getQuery().substring(2));

            runSynchronously(() -> deleteFile(server, exchange, index));
        }
    }

    /**
     * Deletes the file stored at the given index and responds to the HTTP request.
     * Modifies metadata of the leftmost and rightmost files in the tree to update their last and next index values
     * @param server The HttpServer object passed from Handler constructor, used to remove link to the file after deletion
     * @param exchange The HttpExchange object passed from the initial HttpHandler handle method
     * @param index    The index of the file to be deleted
     */
    public void deleteFile(HttpServer server, HttpExchange exchange, int index) {
        if (index <= 0) {
            respond(exchange, 400, "Bad Request: File indexes are 1-based");
            return;
        }

        String metadata = worker.readChunk(0, -index + indexOffset, false, 1);

        if (!DataUtilities.isValidFileMetadata(metadata)) {
            respond(exchange, 400, "Bad Request: File doesn't exist or has corrupted metadata");
            return;
        }

        String targetTitle = DataUtilities.parseTitle(metadata);
        int lastIndex = DataUtilities.parseLastIndexTable(metadata);
        int nextIndex = DataUtilities.parseNextIndexTable(metadata);

        logger.info("target file title: " + targetTitle);
        logger.info("target file lastIndex: " + lastIndex);
        logger.info("target file nextIndex: " + nextIndex);

        // if target file has no last index, update start index to be target's next index
        if (lastIndex == 0) {
            worker.writeToChunk("" + nextIndex, 0, -1, false, 1);
        } else {
            // otherwise, update nextIndex of target's last to be target's nextIndex
            String lastMeta = worker.readChunk(0, -lastIndex + indexOffset, false, 1);
            String lastTitle = DataUtilities.parseTitle(lastMeta);
            String lastMime = DataUtilities.parseFileMime(lastMeta);
            int lastLast = DataUtilities.parseLastIndexTable(lastMeta);

            String newMeta = DataUtilities.fileMetadataBuilder(lastTitle, lastMime, lastLast, nextIndex);

            logger.info("Updating metadata for previous file in the chain, setting nextIndex to " + nextIndex);

            worker.deleteChunk(0, -lastIndex + indexOffset, false, 1);
            worker.writeToChunk(newMeta, 0, -lastIndex + indexOffset, false, 1);

        }

        // if target file has a nextIndex, update nextIndex's last to be target's last
        if (nextIndex != 0) {
            String nextMeta = worker.readChunk(0, -nextIndex + indexOffset, false, 1);
            String nextTitle = DataUtilities.parseTitle(nextMeta);
            String nextMime = DataUtilities.parseFileMime(nextMeta);
            int nextNext = DataUtilities.parseNextIndexTable(nextMeta);

            logger.info("Updating metadata for next file in the chain, setting lastIndex to " + lastIndex);

            String newMeta = DataUtilities.fileMetadataBuilder(nextTitle, nextMime, lastIndex, nextNext);

            worker.deleteChunk(0, -nextIndex + indexOffset, false, 1);
            worker.writeToChunk(newMeta, 0, -nextIndex + indexOffset, false, 1);

        }
        // delete target's metadata
        worker.deleteChunk(0, -index + indexOffset, false, 1);
        // delete target's data
        worker.deleteChunk(1, -index + indexOffset, true, 1);

        deleteSign(index);

        // delete server context related to file
        server.removeContext(DataUtilities.contextNameBuilder(targetTitle));

        respond(exchange, 200, "Deleted file with success: " + targetTitle);

    }

    public void deleteSign(int fileIndex) {
        world.getBlockAt(-1, -63, -(fileIndex * 16) + (indexOffset * 16) - 1).setType(Material.AIR);
    }

}