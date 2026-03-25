package com.decacagle.endpoints;

import com.decacagle.DecaDB;
import com.decacagle.commands.SetUrlCommand;
import com.decacagle.data.DataUtilities;
import com.decacagle.data.DataWorker;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import java.util.logging.Logger;

public class UploadHandler extends APIEndpoint {

    private final HttpServer server;
    private final int indexOffset = -1;

    public UploadHandler(HttpServer server, Logger logger, World world, DecaDB plugin, DataWorker worker) {
        super(logger, world, plugin, worker);
        this.server = server;
    }

    @Override
    public void handle(HttpExchange exchange) {
        addCorsHeaders(exchange);
        if (preflightCheck(exchange)) return;
        runSynchronously(() -> writeFile(exchange));
    }

    public void writeFile(HttpExchange exchange) {
        String uploadBody = parseExchangeBody(exchange);
        if (uploadBody == null) {
            respond(exchange, 413, "Payload Too Large");
            return;
        }

        String[] bodyParts = uploadBody.split(";", 3);

        if (bodyParts.length != 3 || bodyParts[0].isEmpty() || bodyParts[1].isEmpty() || bodyParts[2].isEmpty()) {
            respond(exchange, 400, "Bad Request: Body must be {fileTitle};{mime};{base64Data}");
            return;
        }

        String fileTitle = bodyParts[0].strip();
        String fileMime = bodyParts[1].strip();
        String fileData = bodyParts[2];

        logger.info("Received upload: " + fileTitle + " (" + fileMime + ")");

        int index = getNextIndex();
        int last = index - 1;

        String newFileMetadata = DataUtilities.fileMetadataBuilder(fileTitle, fileMime, last, 0);
        boolean metadataWriteResult = worker.writeToChunk(newFileMetadata, 0, -index + indexOffset, false, 1);

        if (!metadataWriteResult) {
            respond(exchange, 500, "Internal Server Error: Failed to write file metadata");
            return;
        }

        boolean writeFileResult = worker.writeToChunk(fileData, 1, -index + indexOffset, true, 1);
        if (!writeFileResult) {
            respond(exchange, 500, "Internal Server Error: Failed to write file data");
            return;
        }

        updateLastMetadata(index);

        String newContext = DataUtilities.contextNameBuilder(fileTitle);
        try {
            server.createContext(newContext, new FileReader(logger, world, plugin, worker, index));
        } catch (Exception e) {
            respond(exchange, 500, "Internal Server Error: Failed to create route — " + e.getMessage());
            return;
        }

        placeSign(fileTitle, fileMime, index);

        logger.info("Created new route: " + newContext);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        respond(exchange, 200,
                "{\"message\":\"Wrote file " + fileTitle + " successfully!\", \"link\": \""
                        + SetUrlCommand.url + newContext + "\",\"fileId\":" + index + "}");
    }

    public int getNextIndex() {
        String startIndexText = worker.readChunk(0, -1, false, 1);

        if (startIndexText.isEmpty() || startIndexText.equals("0")) {
            worker.writeToChunk("1", 0, -1, false, 1);
            return 1;
        }

        int currentIndex = Integer.parseInt(startIndexText);
        String currentData = worker.readChunk(0, -currentIndex + indexOffset, false, 1);
        int nextIndex = DataUtilities.parseNextIndexTable(currentData);

        while (nextIndex != 0) {
            currentIndex = nextIndex;
            currentData = worker.readChunk(0, -currentIndex + indexOffset, false, 1);
            nextIndex = DataUtilities.parseNextIndexTable(currentData);
        }

        return currentIndex + 1;
    }

    public void placeSign(String fileTitle, String fileMime, int fileIndex) {
        Block block = world.getBlockAt(-1, -63, -(fileIndex * 16) + (indexOffset * 16) - 1);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        sign.setLine(1, fileTitle);
        sign.setLine(2, fileMime);
        sign.update();
    }

    public void updateLastMetadata(int index) {
        if (index == 1) return;

        String metadata = worker.readChunk(0, -(index - 1) + indexOffset, false, 1);
        String title = DataUtilities.parseTitle(metadata);
        String mime = DataUtilities.parseFileMime(metadata);
        int last = DataUtilities.parseLastIndexTable(metadata);

        worker.deleteChunk(0, -(index - 1) + indexOffset, false, 1);
        String newMetadata = DataUtilities.fileMetadataBuilder(title, mime, last, index);
        worker.writeToChunk(newMetadata, 0, -(index - 1) + indexOffset, false, 1);
    }
}