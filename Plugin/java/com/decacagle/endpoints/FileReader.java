package com.decacagle.endpoints;

import com.decacagle.DecaDB;
import com.decacagle.data.DataUtilities;
import com.decacagle.data.DataWorker;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.World;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public class FileReader extends APIEndpoint {

    public final int fileIndex;
    public final int indexOffset = -1;

    public FileReader(Logger logger, World world, DecaDB plugin, DataWorker worker, int fileIndex) {
        super(logger, world, plugin, worker);
        this.fileIndex = fileIndex;
        logger.info("FileReader created for index " + fileIndex);
    }

    @Override
    public void handle(HttpExchange exchange) {
        addCorsHeaders(exchange);
        if (preflightCheck(exchange)) return;
        runSynchronously(() -> readAndServeFile(exchange));
    }

    public void readAndServeFile(HttpExchange exchange) {
        String metadata = worker.readChunk(0, -fileIndex + indexOffset, false, 1);

        if (!DataUtilities.isValidFileMetadata(metadata)) {
            respond(exchange, 404, "Not Found: File metadata missing or corrupt");
            return;
        }

        String fileMime = DataUtilities.parseFileMime(metadata);
        String title = DataUtilities.parseTitle(metadata);

        logger.info("Serving " + title + " as " + fileMime + " from index " + fileIndex);

        String base64Data = worker.readChunk(1, -fileIndex + indexOffset, true, 1);
        byte[] fileBytes;
        try {
            fileBytes = Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            respond(exchange, 500, "Internal Server Error: File data is not valid Base64");
            return;
        }

        String acceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
        boolean clientSupportsGzip = acceptEncoding != null && acceptEncoding.contains("gzip");

        if (clientSupportsGzip && isCompressibleMime(fileMime)) {
            try {
                byte[] compressed = gzipCompress(fileBytes);
                exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                exchange.getResponseHeaders().set("Vary", "Accept-Encoding");
                exchange.getResponseHeaders().add("Content-Type", fileMime);
                respondWithBytes(exchange, 200, compressed);
                return;
            } catch (IOException e) {
                logger.warning("Gzip compression failed for " + title + ", falling back to uncompressed: " + e.getMessage());
            }
        }

        exchange.getResponseHeaders().add("Content-Type", fileMime);
        respondWithBytes(exchange, 200, fileBytes);
    }

    private static boolean isCompressibleMime(String mime) {
        if (mime == null) return false;
        return mime.startsWith("text/")
                || mime.equals("application/json")
                || mime.equals("application/javascript")
                || mime.equals("application/xml")
                || mime.equals("image/svg+xml");
    }

    private static byte[] gzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length / 2);
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(data);
        }
        return bos.toByteArray();
    }
}