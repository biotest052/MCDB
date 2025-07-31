package com.decacagle.endpoints;

import com.decacagle.DecaDB;
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

    private HttpServer server;

    private int indexOffset = -1;

    public UploadHandler(HttpServer server, Logger logger, World world, DecaDB plugin, DataWorker worker) {
        super(logger, world, plugin, worker);

        this.server = server;

    }

    public void handle(HttpExchange exchange) {
        addCorsHeaders(exchange);

        if (!preflightCheck(exchange)) {
            runSynchronously(() -> writeFile(exchange));
        }
    }

    public void writeFile(HttpExchange exchange) {

        String uploadBody = parseExchangeBody(exchange);
        String[] bodyParts = uploadBody.split(";");

        if (bodyParts.length != 3) {
            respond(exchange, 400, "Bad Request: Body of request should be {fileTitle};{mime};{base64Data}, received: " + bodyParts[0] + ";" + bodyParts[1] + ";data");
            return;
        }

        String fileTitle = bodyParts[0];
        String fileMime = bodyParts[1];
        String fileData = bodyParts[2];

        logger.info("Successfully received file upload");
        logger.info("File Title: " + fileTitle);
        logger.info("File Mime: " + fileMime);

        int index = getNextIndex();
        int last = index - 1;

        String newFileMetadata = DataUtilities.fileMetadataBuilder(fileTitle, fileMime, last, 0);

        boolean metadataWriteResult = worker.writeToChunk(newFileMetadata, 0, -index + indexOffset, false, 1);

        if (metadataWriteResult) {

            boolean writeFileResult = worker.writeToChunk(fileData, 1, -index + indexOffset, true, 1);

            if (writeFileResult) {

                updateLastMetadata(index);

                String newContext = DataUtilities.contextNameBuilder(fileTitle);

                try {
                    server.createContext(newContext, new FileReader(logger, world, plugin, worker, index));
                } catch (Exception e) {
                    respond(exchange, 500, "Internal Server Error: Failed to create route -- " + e.getMessage());
                }

                placeSign(fileTitle, fileMime, index);

                logger.info("Created new route: " + newContext);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                respond(exchange, 200, "{\"message\":\"Wrote file " + fileTitle + " successfully!\", \"link\": \"http://localhost:8000" + newContext + "\",\"fileId\":" + index + "}");


            } else {
                respond(exchange, 500, "Internal Server Error: Failed to write base64 data!");
            }

        } else {
            respond(exchange, 400, "Bad Request: Failed to write file metadata!");
        }

    }

    public int getNextIndex() {
        String startIndexText = worker.readChunk(0, -1, false, 1);

        if (startIndexText.isEmpty() || startIndexText.equals("0")) {

            worker.writeToChunk("1", 0, -1, false, 1);

            return 1;
        } else {

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
        if (index != 1) {
            String metadata = worker.readChunk(0, -(index - 1) + indexOffset, false, 1);

            String title = DataUtilities.parseTitle(metadata);
            String mime = DataUtilities.parseFileMime(metadata);
            int last = DataUtilities.parseLastIndexTable(metadata);

            worker.deleteChunk(0, -(index - 1) + indexOffset, false, 1);

            String newMetadata = DataUtilities.fileMetadataBuilder(title, mime, last, index);

            worker.writeToChunk(newMetadata, 0, -(index - 1) + indexOffset, false, 1);

        }

    }

}