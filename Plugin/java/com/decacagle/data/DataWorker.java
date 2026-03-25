package com.decacagle.data;

import com.decacagle.DecaDB;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static com.decacagle.data.DataUtilities.*;

public class DataWorker {

    private final Logger logger;
    private final World world;
    private final DecaDB plugin;

    private final ConcurrentHashMap<String, Integer> tableIndexCache = new ConcurrentHashMap<>();

    public DataWorker(Logger logger, World world, DecaDB plugin) {
        this.logger = logger;
        this.world = world;
        this.plugin = plugin;
    }

    public boolean writeToChunk(String body, int xIndex, int zIndex, boolean writeInfinitely, int direction) {
        int indexX = xIndex * 16;
        int indexZ = -1 + (zIndex * 16);

        StringBuilder res = new StringBuilder(body.length() * 2);
        for (int i = 0; i < body.length(); i++) {
            res.append(asciiToHex(body.charAt(i)));
        }

        int x = indexX;
        int z = indexZ;
        int y = -64;

        for (int i = 0; i < res.length(); i++) {
            Block current = world.getBlockAt(x, y, z);
            Material targetMat = getCorrespondingBlock(res.charAt(i));

            if (current.getType() != targetMat) {
                current.setType(targetMat, false);
            }

            x++;

            if (x >= (indexX + 16)) {
                if (z <= indexZ - 15) {
                    x = indexX;
                    z = indexZ;
                    y++;
                } else {
                    x = indexX;
                    z--;
                }
            }

            if (y >= 320) {
                if (writeInfinitely) {
                    xIndex += direction;
                    indexX = xIndex * 16;
                    x = indexX;
                    z = indexZ;
                    y = -64;
                } else {
                    logger.info("Ran out of build height, discontinuing write!");
                    return false;
                }
            }
        }

        return true;
    }

    public String readChunk(int xIndex, int zIndex, boolean readInfinitely, int direction) {
        int startX = xIndex * 16;
        int startZ = -1 + (zIndex * 16);

        int x = startX;
        int z = startZ;
        int y = -64;

        StringBuilder hexBuilder = new StringBuilder();
        boolean reading = true;

        while (reading) {
            Block current = world.getBlockAt(x, y, z);
            char presentChar = getCorrespondingChar(current.getType());
            if (presentChar != 'n') {
                hexBuilder.append(presentChar);
            } else {
                reading = false;
            }

            x++;

            if (x >= (startX + 16)) {
                if (z <= startZ - 15) {
                    x = startX;
                    z = startZ;
                    y++;
                } else {
                    x = startX;
                    z--;
                }
            }

            if (y >= 320) {
                if (readInfinitely) {
                    xIndex += direction;
                    startX = xIndex * 16;
                    x = startX;
                    z = startZ;
                    y = -64;
                } else {
                    logger.info("Ran out of build height, discontinuing read!");
                    return "Failed";
                }
            }
        }

        StringBuilder asciiBuilder = new StringBuilder(hexBuilder.length() / 2);
        for (int i = 0; i + 1 < hexBuilder.length(); i += 2) {
            String hexByte = "" + hexBuilder.charAt(i) + hexBuilder.charAt(i + 1);
            asciiBuilder.append(hexToAscii(hexByte));
        }

        return asciiBuilder.toString();
    }

    public void deleteChunk(int xIndex, int zIndex, boolean readInfinitely, int direction) {
        int startX = xIndex * 16;
        int startZ = -1 + (zIndex * 16);

        int x = startX;
        int z = startZ;
        int y = -64;
        boolean stillData = true;

        while (stillData) {
            Block current = world.getBlockAt(x, y, z);

            if (isWoolBlock(current.getType())) {
                Material replacement = (y == -64) ? Material.GRASS_BLOCK : Material.AIR;
                if (current.getType() != replacement) {
                    current.setType(replacement, false);
                }
            } else {
                stillData = false;
                break;
            }

            x++;

            if (x >= (startX + 16)) {
                if (z <= startZ - 15) {
                    x = startX;
                    z = startZ;
                    y++;
                } else {
                    x = startX;
                    z--;
                }
            }

            if (y >= 320) {
                if (readInfinitely) {
                    xIndex += direction;
                    startX = xIndex * 16;
                    x = startX;
                    z = startZ;
                    y = -64;
                } else {
                    logger.info("Ran out of build height, discontinuing delete!");
                    return;
                }
            }
        }
    }

    public int getTableIndex(String tableTitle, int indexOffset) {
        if (tableTitle == null || tableTitle.isEmpty()) return 0;

        Integer cached = tableIndexCache.get(tableTitle);
        if (cached != null) return cached;

        int index = scanForTableIndex(tableTitle, indexOffset);
        if (index != 0) {
            tableIndexCache.put(tableTitle, index);
        }
        return index;
    }

    public void invalidateTableCache(String tableTitle) {
        tableIndexCache.remove(tableTitle);
    }

    public void clearTableCache() {
        tableIndexCache.clear();
    }

    private int scanForTableIndex(String tableTitle, int indexOffset) {
        String tableStartIndex = readChunk(0, 1, false, 1);

        if (tableStartIndex.isEmpty() || tableStartIndex.equals("0")) {
            return 0;
        }

        int currentIndex = Integer.parseInt(tableStartIndex);

        while (currentIndex != 0) {
            String currentMetadata = readChunk(0, currentIndex + indexOffset, false, 1);
            String title = DataUtilities.parseTitle(currentMetadata);
            int nextIndex = DataUtilities.parseNextIndexTable(currentMetadata);

            logger.info("Scanning table: " + title);

            if (title.equals(tableTitle)) return currentIndex;

            currentIndex = nextIndex;
        }

        return 0;
    }
}