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

            // On server launch, read through list of current saved files and create routes for those files
            addRoutes(server);

            server.setExecutor(null);
            server.start();

            logger.info("HTTP Server started!");

        } catch (IOException e) {
            logger.info("Error: " + e.getMessage());
        }
    }

    public void addRoutes(HttpServer server) {

        try {
            Bukkit.getScheduler().runTask(plugin, () -> addFileRoutes(server));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    int indexOffset = -1;

    public static String getMetadata(Player player, CommandSender sender)
    {
        Location loc = player.getLocation();
        int chunkX = (int)Math.floor(loc.getX() / 16);
        int chunkZ = (int)Math.floor(loc.getZ() / 16) + 1;

        String startIndex = worker.readChunk(chunkX, chunkZ, false, 1);

        if (!startIndex.isEmpty())
            return startIndex;

        return "Couldnt find metadata.";
    }

    public static boolean writeMetadata(Player player, CommandSender sender, String base64Text, String path)
    {
        Location loc = player.getLocation();
        int chunkX = (int)Math.floor(loc.getX() / 16);
        int chunkZ = (int)Math.floor(loc.getZ() / 16) + 1;

        Path filePath = Paths.get(path);

        String fileName = filePath.getFileName().toString();
        System.out.println("File name: " + fileName);

        try {
            String mimeType = Files.probeContentType(filePath);
            System.out.println("MIME type: " + mimeType);

            worker.deleteChunk(chunkX - 1, chunkZ, false, 1);

            String newFileMetadata = DataUtilities.fileMetadataBuilder(fileName, mimeType, Math.abs(chunkZ + 2), Math.abs(chunkZ + 2) + 2);
            newFileMetadata = newFileMetadata.replace("javascriptt", "javascript");

            boolean written = worker.writeToChunk(newFileMetadata, chunkX - 1, chunkZ, false, 1);

            if (written) {
                worker.deleteChunk(chunkX, chunkZ, true, 1);

                boolean writeFileResult = worker.writeToChunk(base64Text, chunkX, chunkZ, true, 1);

                if (writeFileResult) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            System.err.println("Failed to determine MIME type: " + e.getMessage());
            return false;
        }


    }

    public void addFileRoutes(HttpServer server) {

        String startIndex = worker.readChunk(0, -1, false, 1);

        if (!startIndex.isEmpty() && !startIndex.equals("0")) {

            int currentIndex = Integer.parseInt(startIndex);
            String currentMetadata = worker.readChunk(0, -currentIndex + indexOffset, false, 1);

            String title = DataUtilities.parseTitle(currentMetadata);
            int nextIndex = DataUtilities.parseNextIndexTable(currentMetadata);

            server.createContext(DataUtilities.contextNameBuilder(title), new FileReader(logger, world, plugin, worker, currentIndex));

            while (nextIndex != 0) {

                currentIndex = nextIndex;
                currentMetadata = worker.readChunk(0, -currentIndex + indexOffset, false, 1);
                title = DataUtilities.parseTitle(currentMetadata);
                nextIndex = DataUtilities.parseNextIndexTable(currentMetadata);

                server.createContext(DataUtilities.contextNameBuilder(title), new FileReader(logger, world, plugin, worker, currentIndex));

            }

        }

    }

}
