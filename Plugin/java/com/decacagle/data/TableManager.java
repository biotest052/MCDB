package com.decacagle.data;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class TableManager {

    private final Logger logger;
    private final World world;
    private final DataWorker worker;

    private final int indexOffset = 1;

    public TableManager(Logger logger, World world, DataWorker worker) {
        this.logger = logger;
        this.world = world;
        this.worker = worker;
    }

    public MethodResponse insertRow(String tableTitle, String rowData) {
        if (rowData == null || rowData.isEmpty()) {
            return new MethodResponse(400, "Bad Request: Row data is empty", null, true);
        }
        if (tableTitle == null || tableTitle.isEmpty()) {
            return new MethodResponse(400, "Bad Request: Table title is empty", null, true);
        }

        logger.info("INSERT into " + tableTitle);

        int tableIndex = worker.getTableIndex(tableTitle, indexOffset);
        if (tableIndex == 0) {
            return new MethodResponse(400, "Bad Request: No table named '" + tableTitle + "' exists", null, true);
        }

        int index = getNextRowIndex(tableIndex);
        int last = index - 1;

        String rowDataWithId = DataUtilities.addValueToJSON(index, "id", rowData);
        String newRowData = DataUtilities.rowBuilder(last, 0, rowDataWithId);

        boolean written = worker.writeToChunk(newRowData, index + indexOffset, tableIndex + indexOffset, false, 1);
        if (!written) {
            return new MethodResponse(500, "Internal Server Error: Failed to write row — is the data too large?", null, true);
        }

        updateLastRowMetadata(index, tableIndex);
        return new MethodResponse(200, "Wrote row successfully", rowDataWithId, false);
    }

    public int getNextRowIndex(int tableIndex) {
        String startIndexText = worker.readChunk(1, tableIndex + 1, false, 1);

        if (startIndexText.isEmpty() || startIndexText.equals("0")) {
            worker.writeToChunk("1", 1, tableIndex + 1, false, 1);
            return 1;
        }

        int currentIndex = Integer.parseInt(startIndexText);
        String currentData = worker.readChunk(currentIndex + indexOffset, tableIndex + indexOffset, false, 1);
        int nextIndex = DataUtilities.parseNextIndexRow(currentData);

        while (nextIndex != 0) {
            currentIndex = nextIndex;
            currentData = worker.readChunk(currentIndex + indexOffset, tableIndex + indexOffset, false, 1);
            nextIndex = DataUtilities.parseNextIndexRow(currentData);
        }

        return currentIndex + 1;
    }

    public void updateLastRowMetadata(int index, int tableIndex) {
        if (index == 1) return;

        String metadata = worker.readChunk((index - 1) + indexOffset, tableIndex + indexOffset, false, 1);
        int last = DataUtilities.parseLastIndexRow(metadata);
        String rowContent = DataUtilities.parseRowContent(metadata);

        worker.deleteChunk((index - 1) + indexOffset, tableIndex + indexOffset, false, 1);
        String newMetadata = DataUtilities.rowBuilder(last, index, rowContent);
        worker.writeToChunk(newMetadata, (index - 1) + indexOffset, tableIndex + indexOffset, false, 1);
    }

    public MethodResponse updateRow(String tableTitle, int rowId, String content) {
        if (content == null || content.isEmpty()) {
            return new MethodResponse(400, "Bad Request: No new data provided", null, true);
        }

        int tableIndex = worker.getTableIndex(tableTitle, indexOffset);
        if (tableIndex == 0) {
            return new MethodResponse(400, "Bad Request: Table doesn't exist or has corrupted metadata", null, true);
        }

        String currentData = worker.readChunk(rowId + indexOffset, tableIndex + indexOffset, false, 1);
        if (currentData.isEmpty()) {
            return new MethodResponse(400, "Bad Request: No row with id " + rowId + " in table " + tableTitle, null, true);
        }

        int lastIndex = DataUtilities.parseLastIndexRow(currentData);
        int nextIndex = DataUtilities.parseNextIndexRow(currentData);

        worker.deleteChunk(rowId + indexOffset, tableIndex + indexOffset, false, 1);
        worker.writeToChunk(DataUtilities.rowBuilder(lastIndex, nextIndex, content),
                rowId + indexOffset, tableIndex + indexOffset, false, 1);

        return new MethodResponse(200, "Updated id " + rowId + " in " + tableTitle, "Updated id " + rowId, false);
    }

    public MethodResponse readTable(String tableTitle) {
        if (tableTitle == null || tableTitle.isEmpty()) {
            return new MethodResponse(400, "Bad Request: No table title provided", null, true);
        }

        int tableIndex = worker.getTableIndex(tableTitle, indexOffset);
        if (tableIndex == 0) {
            return new MethodResponse(400, "Bad Request: Table doesn't exist or has corrupted metadata", null, true);
        }

        return new MethodResponse(200, "OK", readAllRows(tableIndex), false);
    }

    public MethodResponse readTableWithCondition(String tableTitle, String key, String target) {
        if (tableTitle == null || tableTitle.isEmpty()) {
            return new MethodResponse(400, "Bad Request: No table title provided", null, true);
        }

        int tableIndex = worker.getTableIndex(tableTitle, indexOffset);
        if (tableIndex == 0) {
            return new MethodResponse(400, "Bad Request: Table doesn't exist or has corrupted metadata", null, true);
        }

        return new MethodResponse(200, "OK", gatherRowsWithCondition(tableIndex, key, target), false);
    }

    public String gatherRowsWithCondition(int tableIndex, String key, String target) {
        List<String> matching = new ArrayList<>();

        String tableStartIndex = worker.readChunk(1, tableIndex + indexOffset, false, 1);
        if (tableStartIndex.isEmpty() || tableStartIndex.equals("0")) return "[]";

        int currentIndex = Integer.parseInt(tableStartIndex);

        while (currentIndex != 0) {
            String currentRow = worker.readChunk(currentIndex + 1, tableIndex + 1, false, 1);
            String content = DataUtilities.parseRowContent(currentRow);
            int nextIndex = DataUtilities.parseNextIndexRow(currentRow);

            if (DataUtilities.meetsCondition(content, key, target)) {
                matching.add(content);
            }

            currentIndex = nextIndex;
        }

        return "[" + String.join(",", matching) + "]";
    }

    public String readAllRows(int tableIndex) {
        List<String> rows = new ArrayList<>();

        String tableStartIndex = worker.readChunk(1, tableIndex + indexOffset, false, 1);
        if (tableStartIndex.isEmpty() || tableStartIndex.equals("0")) return "[]";

        int currentIndex = Integer.parseInt(tableStartIndex);

        while (currentIndex != 0) {
            String currentRow = worker.readChunk(currentIndex + indexOffset, tableIndex + indexOffset, false, 1);
            String content = DataUtilities.parseRowContent(currentRow);
            int nextIndex = DataUtilities.parseNextIndexRow(currentRow);
            rows.add(content);
            currentIndex = nextIndex;
        }

        return "[" + String.join(",", rows) + "]";
    }

    public MethodResponse readRow(String tableTitle, int rowIndex) {
        if (rowIndex <= 0) {
            return new MethodResponse(400, "Bad Request: Row id must be >= 1, got " + rowIndex, null, true);
        }
        if (tableTitle == null || tableTitle.isEmpty()) {
            return new MethodResponse(400, "Bad Request: No table title provided", null, true);
        }

        int tableIndex = worker.getTableIndex(tableTitle, indexOffset);
        if (tableIndex == 0) {
            return new MethodResponse(400, "Bad Request: Table doesn't exist or has corrupted metadata", null, true);
        }

        String rowData = worker.readChunk(rowIndex + indexOffset, tableIndex + indexOffset, false, 1);
        if (rowData.isEmpty()) {
            return new MethodResponse(400, "Bad Request: Row doesn't exist or has corrupted metadata", null, true);
        }

        return new MethodResponse(200, "OK", DataUtilities.parseRowContent(rowData), false);
    }

    public MethodResponse createTable(String tableTitle) {
        if (tableTitle == null || tableTitle.isEmpty()) {
            return new MethodResponse(400, "Bad Request: Table title is empty", null, true);
        }

        if (worker.getTableIndex(tableTitle, indexOffset) != 0) {
            return new MethodResponse(400, "Bad Request: A table named '" + tableTitle + "' already exists", null, true);
        }

        logger.info("Creating table: " + tableTitle);

        int index = getNextTableIndex();
        int last = index - 1;

        String newMetadata = DataUtilities.tableMetadataBuilder(tableTitle, last, 0);
        boolean written = worker.writeToChunk(newMetadata, 0, index + indexOffset, false, 1);

        if (!written) {
            return new MethodResponse(400, "Bad Request: Failed to write table metadata", null, true);
        }

        updateLastTableMetadata(index);
        placeTableSign(tableTitle, index);

        // Cache the new table immediately
        worker.invalidateTableCache(tableTitle);

        return new MethodResponse(200, "Created table '" + tableTitle + "'", "Created table '" + tableTitle + "'", false);
    }

    public int getNextTableIndex() {
        String startIndexText = worker.readChunk(0, 1, false, 1);

        if (startIndexText.isEmpty() || startIndexText.equals("0")) {
            worker.writeToChunk("1", 0, 1, false, 1);
            return 1;
        }

        int currentIndex = Integer.parseInt(startIndexText);
        String currentData = worker.readChunk(0, currentIndex + indexOffset, false, 1);
        int nextIndex = DataUtilities.parseNextIndexTable(currentData);

        while (nextIndex != 0) {
            currentIndex = nextIndex;
            currentData = worker.readChunk(0, currentIndex + indexOffset, false, 1);
            nextIndex = DataUtilities.parseNextIndexTable(currentData);
        }

        return currentIndex + 1;
    }

    public void placeTableSign(String title, int index) {
        Block block = world.getBlockAt(-1, -63, (index * 16) + (indexOffset * 16) - 1);
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();
        sign.setLine(1, title);
        sign.update();
    }

    public void updateLastTableMetadata(int index) {
        if (index == 1) return;

        String metadata = worker.readChunk(0, (index - 1) + indexOffset, false, 1);
        String title = DataUtilities.parseTitle(metadata);
        int last = DataUtilities.parseLastIndexTable(metadata);

        worker.deleteChunk(0, (index - 1) + indexOffset, false, 1);
        String newMetadata = DataUtilities.tableMetadataBuilder(title, last, index);
        worker.writeToChunk(newMetadata, 0, (index - 1) + indexOffset, false, 1);
    }

    public MethodResponse deleteTable(String tableTitle) {
        int tableIndex = worker.getTableIndex(tableTitle, indexOffset);
        if (tableIndex == 0) {
            return new MethodResponse(400, "Bad Request: No table named '" + tableTitle + "' exists", "", true);
        }
        MethodResponse result = deleteTable(tableIndex);
        worker.invalidateTableCache(tableTitle);
        return result;
    }

    public MethodResponse deleteTable(int index) {
        if (index <= 0) {
            return new MethodResponse(400, "Bad Request: Table index must be >= 1", "", true);
        }

        String metadata = worker.readChunk(0, index + indexOffset, false, 1);
        if (!DataUtilities.isValidTableMetadata(metadata)) {
            return new MethodResponse(400, "Bad Request: Table doesn't exist or has corrupted metadata", "", true);
        }

        String targetTitle = DataUtilities.parseTitle(metadata);
        int lastIndex = DataUtilities.parseLastIndexTable(metadata);
        int nextIndex = DataUtilities.parseNextIndexTable(metadata);

        if (lastIndex == 0) {
            worker.writeToChunk("" + nextIndex, 0, 1, false, 1);
        } else {
            String lastMeta = worker.readChunk(0, lastIndex + indexOffset, false, 1);
            String lastTitle = DataUtilities.parseTitle(lastMeta);
            int lastLast = DataUtilities.parseLastIndexTable(lastMeta);
            worker.deleteChunk(0, lastIndex + indexOffset, false, 1);
            worker.writeToChunk(DataUtilities.tableMetadataBuilder(lastTitle, lastLast, nextIndex),
                    0, lastIndex + indexOffset, false, 1);
        }

        if (nextIndex != 0) {
            String nextMeta = worker.readChunk(0, nextIndex + indexOffset, false, 1);
            String nextTitle = DataUtilities.parseTitle(nextMeta);
            int nextNext = DataUtilities.parseNextIndexTable(nextMeta);
            worker.deleteChunk(0, nextIndex + indexOffset, false, 1);
            worker.writeToChunk(DataUtilities.tableMetadataBuilder(nextTitle, lastIndex, nextNext),
                    0, nextIndex + indexOffset, false, 1);
        }

        deleteTableSign(index);
        int rowsDeleted = deleteAllRows(index);

        worker.deleteChunk(1, index + indexOffset, false, 1);
        worker.deleteChunk(0, index + indexOffset, false, 1);
        worker.invalidateTableCache(targetTitle);

        return new MethodResponse(200,
                "Deleted table '" + targetTitle + "'. Rows deleted: " + rowsDeleted,
                "Deleted table '" + targetTitle + "'. Rows deleted: " + rowsDeleted,
                false);
    }

    public int deleteAllRows(int tableIndex) {
        String tableStartIndex = worker.readChunk(1, tableIndex + indexOffset, false, 1);
        if (tableStartIndex.isEmpty() || tableStartIndex.equals("0")) return 0;

        int counter = 0;
        int currentIndex = Integer.parseInt(tableStartIndex);

        while (currentIndex != 0) {
            String currentRow = worker.readChunk(currentIndex + indexOffset, tableIndex + indexOffset, false, 1);
            int nextIndex = DataUtilities.parseNextIndexRow(currentRow);
            worker.deleteChunk(currentIndex + indexOffset, tableIndex + indexOffset, false, 1);
            currentIndex = nextIndex;
            counter++;
        }

        worker.writeToChunk("0", 1, tableIndex + indexOffset, false, 1);
        return counter;
    }

    public int deleteAllRowsWithCondition(int tableIndex, String key, String target) {
        String tableStartIndex = worker.readChunk(1, tableIndex + indexOffset, false, 1);
        if (tableStartIndex.isEmpty() || tableStartIndex.equals("0")) return 0;

        int counter = 0;
        int currentIndex = Integer.parseInt(tableStartIndex);

        while (currentIndex != 0) {
            String currentRow = worker.readChunk(currentIndex + indexOffset, tableIndex + indexOffset, false, 1);
            int nextIndex = DataUtilities.parseNextIndexRow(currentRow);
            String content = DataUtilities.parseRowContent(currentRow);

            if (DataUtilities.meetsCondition(content, key, target)) {
                deleteRow(tableIndex, currentIndex);
                counter++;
            }

            currentIndex = nextIndex;
        }

        return counter;
    }

    public void deleteTableSign(int index) {
        world.getBlockAt(-1, -63, (index * 16) + (indexOffset * 16) - 1).setType(Material.AIR);
    }

    public MethodResponse deleteAllFromTable(String tableTitle) {
        int tableIndex = worker.getTableIndex(tableTitle, indexOffset);
        if (tableIndex == 0) {
            return new MethodResponse(400, "Bad Request: No table named '" + tableTitle + "' exists", null, true);
        }
        int rowsDeleted = deleteAllRows(tableIndex);
        return new MethodResponse(200,
                "Deleted all rows from " + tableTitle + ". Rows deleted: " + rowsDeleted,
                "Deleted all rows from " + tableTitle + ". Rows deleted: " + rowsDeleted,
                false);
    }

    public MethodResponse deleteAllFromTableWithCondition(String tableTitle, String key, String target) {
        int tableIndex = worker.getTableIndex(tableTitle, indexOffset);
        if (tableIndex == 0) {
            return new MethodResponse(400, "Bad Request: No table named '" + tableTitle + "' exists", null, true);
        }
        int rowsDeleted = deleteAllRowsWithCondition(tableIndex, key, target);
        return new MethodResponse(200,
                "Deleted matching rows from " + tableTitle + ". Rows deleted: " + rowsDeleted,
                "Deleted matching rows from " + tableTitle + ". Rows deleted: " + rowsDeleted,
                false);
    }

    public MethodResponse deleteRow(String tableTitle, int rowIndex) {
        if (rowIndex <= 0) {
            return new MethodResponse(400, "Bad Request: Row id must be >= 1", null, true);
        }
        if (tableTitle == null || tableTitle.isEmpty()) {
            return new MethodResponse(400, "Bad Request: No table name provided", null, true);
        }

        int tableIndex = worker.getTableIndex(tableTitle, indexOffset);
        if (tableIndex == 0) {
            return new MethodResponse(400, "Bad Request: Table doesn't exist or has corrupted metadata", null, true);
        }

        return deleteRow(tableIndex, rowIndex);
    }

    public MethodResponse deleteRow(int tableIndex, int rowIndex) {
        if (rowIndex <= 0) {
            return new MethodResponse(400, "Bad Request: Row id must be >= 1", null, true);
        }

        String rowData = worker.readChunk(rowIndex + indexOffset, tableIndex + indexOffset, false, 1);
        if (rowData.isEmpty()) {
            return new MethodResponse(400, "Bad Request: Row doesn't exist or has corrupted metadata", null, true);
        }

        int lastIndex = DataUtilities.parseLastIndexRow(rowData);
        int nextIndex = DataUtilities.parseNextIndexRow(rowData);

        if (lastIndex == 0) {
            worker.deleteChunk(1, tableIndex + indexOffset, false, 1);
            worker.writeToChunk("" + nextIndex, 1, tableIndex + indexOffset, false, 1);
        } else {
            String lastRowData = worker.readChunk(lastIndex + indexOffset, tableIndex + indexOffset, false, 1);
            String lastContent = DataUtilities.parseRowContent(lastRowData);
            int lastLast = DataUtilities.parseLastIndexRow(lastRowData);
            worker.deleteChunk(lastIndex + indexOffset, tableIndex + indexOffset, false, 1);
            worker.writeToChunk(DataUtilities.rowBuilder(lastLast, nextIndex, lastContent),
                    lastIndex + indexOffset, tableIndex + indexOffset, false, 1);
        }

        if (nextIndex != 0) {
            String nextMeta = worker.readChunk(nextIndex + indexOffset, tableIndex + indexOffset, false, 1);
            String nextContent = DataUtilities.parseRowContent(nextMeta);
            int nextNext = DataUtilities.parseNextIndexRow(nextMeta);
            worker.deleteChunk(nextIndex + indexOffset, tableIndex + indexOffset, false, 1);
            worker.writeToChunk(DataUtilities.rowBuilder(lastIndex, nextNext, nextContent),
                    nextIndex + indexOffset, tableIndex + indexOffset, false, 1);
        }

        worker.deleteChunk(rowIndex + indexOffset, tableIndex + indexOffset, false, 1);
        return new MethodResponse(200, "Deleted row " + rowIndex, "Deleted row " + rowIndex, false);
    }

    public MethodResponse protectTable(String tableTitle, String protection) {
        if (tableTitle == null || tableTitle.isEmpty()) {
            return new MethodResponse(400, "Bad Request: Table title is empty", null, true);
        }
        if (protection == null || protection.isEmpty()) {
            return new MethodResponse(400, "Bad Request: Protection flags are empty", null, true);
        }

        MethodResponse checkFlags = DataUtilities.areValidProtectionFlags(protection);
        if (checkFlags.hasError()) return checkFlags;

        int tableIndex = worker.getTableIndex(tableTitle, indexOffset);
        if (tableIndex == 0) {
            return new MethodResponse(400, "Bad Request: Table doesn't exist or has corrupted metadata", null, true);
        }

        String tableMetadata = worker.readChunk(0, tableIndex + indexOffset, false, 1);
        if (!DataUtilities.isValidTableMetadata(tableMetadata)) {
            return new MethodResponse(500, "Internal Server Error: Metadata for '" + tableTitle + "' is corrupt", null, true);
        }

        String protectionField = DataUtilities.tableProtectionBuilder(protection);
        String newMetadata = DataUtilities.generateProtectedMetadata(tableMetadata, protectionField);

        worker.deleteChunk(0, tableIndex + indexOffset, false, 1);
        worker.writeToChunk(newMetadata, 0, tableIndex + indexOffset, false, 1);
        worker.invalidateTableCache(tableTitle);

        return new MethodResponse(200, "Updated protection for '" + tableTitle + "'", "Updated protection", false);
    }

    public MethodResponse removeProtections(String tableTitle) {
        if (tableTitle == null || tableTitle.isEmpty()) {
            return new MethodResponse(400, "Bad Request: Table title is empty", null, true);
        }

        int tableIndex = worker.getTableIndex(tableTitle, indexOffset);
        if (tableIndex == 0) {
            return new MethodResponse(400, "Bad Request: Table doesn't exist or has corrupted metadata", null, true);
        }

        String tableMetadata = worker.readChunk(0, tableIndex + indexOffset, false, 1);
        if (!DataUtilities.isValidTableMetadata(tableMetadata)) {
            return new MethodResponse(500, "Internal Server Error: Metadata for '" + tableTitle + "' is corrupt", null, true);
        }

        int last = DataUtilities.parseLastIndexTable(tableMetadata);
        int next = DataUtilities.parseNextIndexTable(tableMetadata);
        String title = DataUtilities.parseTitle(tableMetadata);

        String newMetadata = DataUtilities.tableMetadataBuilder(title, last, next);
        worker.deleteChunk(0, tableIndex + indexOffset, false, 1);
        worker.writeToChunk(newMetadata, 0, tableIndex + indexOffset, false, 1);
        worker.invalidateTableCache(tableTitle);

        return new MethodResponse(200, "Removed protections from '" + tableTitle + "'", "Removed protections", false);
    }

    public MethodResponse getProtectionFlags(String tableTitle) {
        if (tableTitle == null || tableTitle.isEmpty()) {
            return new MethodResponse(400, "Bad Request: Table title is empty", null, true);
        }

        int tableIndex = worker.getTableIndex(tableTitle, indexOffset);
        if (tableIndex == 0) {
            return new MethodResponse(400, "Bad Request: Table doesn't exist or has corrupted metadata", null, true);
        }

        String metadata = worker.readChunk(0, tableIndex + indexOffset, false, 1);
        if (!DataUtilities.isValidTableMetadata(metadata)) {
            return new MethodResponse(500, "Internal Server Error: Metadata for '" + tableTitle + "' is corrupt", null, true);
        }

        if (!DataUtilities.tableHasProtectionFlags(metadata)) {
            return new MethodResponse(200, "", "", false);
        }

        String flags = DataUtilities.parseTableProtectionFlags(metadata);
        if (DataUtilities.areValidProtectionFlags(flags).hasError()) {
            return new MethodResponse(500, "Internal Server Error: Invalid protection flags stored for '" + tableTitle + "': " + flags, null, true);
        }

        return new MethodResponse(200, flags, flags, false);
    }
}