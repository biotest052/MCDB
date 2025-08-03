package com.decacagle.endpoints;

import com.decacagle.DecaDB;
import com.decacagle.data.DataUtilities;
import com.decacagle.data.DataWorker;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.World;

import java.util.Base64;
import java.util.logging.Logger;

public class FileReader extends APIEndpoint {

    public int fileIndex;

    public int indexOffset = -1;

    public FileReader(Logger logger, World world, DecaDB plugin, DataWorker worker, int fileIndex) {
        super(logger, world, plugin, worker);

        // TODO: This could just be created within APIEndpoint
        this.worker = new DataWorker(logger, world, plugin);

        this.fileIndex = fileIndex;

        logger.info("FileReader created for index " + fileIndex);

    }

    public void handle(HttpExchange exchange) {
        addCorsHeaders(exchange);

        if (!preflightCheck(exchange)) {
            runSynchronously(() -> readAndServeFile(exchange));
        }

    }

    public void readAndServeFile(HttpExchange exchange) {

        String metadata = worker.readChunk(0, -fileIndex + indexOffset, false, 1);

        String fileMime = DataUtilities.parseFileMime(metadata);

        logger.info("Serving " + DataUtilities.parseTitle(metadata) + " as " + fileMime + " from index " + fileIndex);

        String base64Data = worker.readChunk(1, -fileIndex + indexOffset, true, 1);

        byte[] fileBytes = Base64.getDecoder().decode(base64Data);

        exchange.getResponseHeaders().add("Content-Type", fileMime);

        respondWithBytes(exchange, 200, fileBytes);

    }

}
