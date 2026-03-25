package com.decacagle;

import com.decacagle.commands.SetUrlCommand;
import com.decacagle.data.DataUtilities;
import com.decacagle.data.DataWorker;
import com.decacagle.endpoints.*;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class APIManager {

    private Logger logger;
    private World world;
    private DecaDB plugin;
    public static DataWorker worker;

    public APIManager(Logger logger, World world, DecaDB plugin) {
        this.logger = logger;
        this.world = world;
        this.plugin = plugin;
        this.worker = new DataWorker(logger, world, plugin);
        startHTTPServer();
    }

    public void startHTTPServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
            SetUrlCommand.url = "http://localhost:" + server.getAddress().getPort();

            server.createContext("/upload", new UploadHandler(server, logger, world, plugin, worker));
            server.createContext("/deleteFile", new DeleteFileHandler(server, logger, world, plugin, worker));
            server.createContext("/query", new QueryHandler(logger, world, plugin, worker));
            server.createContext("/admin/raw", new RawHandler(logger, world, plugin, worker));

            addRoutes(server);

            server.setExecutor(null);
            server.start();

            logger.info("HTTP Server started on port " + server.getAddress().getPort());

        } catch (IOException e) {
            logger.severe("Failed to start HTTP server: " + e.getMessage());
        }
    }

    public void addRoutes(HttpServer server) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String startIndex = worker.readChunk(0, -1, false, 1);

                if (startIndex.isEmpty() || startIndex.equals("0")) return;

                int currentIndex = Integer.parseInt(startIndex);

                while (currentIndex != 0) {
                    final int idx = currentIndex;
                    String currentMetadata = worker.readChunk(0, -idx + (-1), false, 1);

                    if (!DataUtilities.isValidFileMetadata(currentMetadata)) break;

                    String title = DataUtilities.parseTitle(currentMetadata);
                    int nextIndex = DataUtilities.parseNextIndexTable(currentMetadata);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String contextName = DataUtilities.contextNameBuilder(title);
                        server.createContext(contextName, new FileReader(logger, world, plugin, worker, idx));
                        logger.info("Registered file route: " + contextName);
                    });

                    currentIndex = nextIndex;
                }
            } catch (Exception e) {
                logger.warning("Error loading file routes: " + e.getMessage());
            }
        });
    }

    public static String getMetadata(Player player, CommandSender sender) {
        Location loc = player.getLocation();
        int chunkX = (int) Math.floor(loc.getX() / 16);
        int chunkZ = (int) Math.floor(loc.getZ() / 16) + 1;

        String startIndex = worker.readChunk(chunkX, chunkZ, false, 1);

        if (!startIndex.isEmpty())
            return startIndex;

        return "Couldn't find metadata.";
    }

    public static boolean writeMetadata(Player player, CommandSender sender, String base64Text, String path) {
        Location loc = player.getLocation();
        int chunkX = (int) Math.floor(loc.getX() / 16);
        int chunkZ = (int) Math.floor(loc.getZ() / 16) + 1;

        Path filePath = Paths.get(path);
        String fileName = filePath.getFileName().toString();

        try {
            String mimeType = Files.probeContentType(filePath);
            if (mimeType == null) mimeType = "application/octet-stream";

            worker.deleteChunk(chunkX - 1, chunkZ, false, 1);

            String newFileMetadata = DataUtilities.fileMetadataBuilder(fileName, mimeType, Math.abs(chunkZ + 2), Math.abs(chunkZ + 2) + 2);
            newFileMetadata = newFileMetadata.replace("javascriptt", "javascript");

            boolean written = worker.writeToChunk(newFileMetadata, chunkX - 1, chunkZ, false, 1);

            if (written) {
                worker.deleteChunk(chunkX, chunkZ, true, 1);
                return worker.writeToChunk(base64Text, chunkX, chunkZ, true, 1);
            }
            return false;
        } catch (IOException e) {
            System.err.println("Failed to determine MIME type: " + e.getMessage());
            return false;
        }
    }
}