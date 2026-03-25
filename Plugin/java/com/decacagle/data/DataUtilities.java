package com.decacagle.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

public final class DataUtilities {

    private static final long AUTH_TOKEN_LIFESPAN_DAYS = 7;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<Character> VALID_FLAG_CHARS = Set.of('c', 'r', 'u', 'd', '*');
    private static final Pattern SAFE_IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    public static boolean isSafeIdentifier(String s) {
        return s != null && !s.isEmpty() && SAFE_IDENTIFIER_PATTERN.matcher(s).matches();
    }

    public static String asciiToHex(char c) {
        return Integer.toHexString((int) (c));
    }

    public static char hexToAscii(String hex) {
        return (char) Integer.parseInt(hex, 16);
    }

    public static Material getCorrespondingBlock(char inputC) {
        char c = ("" + inputC).toUpperCase().charAt(0);
        return switch (c) {
            case '0' -> Material.WHITE_WOOL;
            case '1' -> Material.ORANGE_WOOL;
            case '2' -> Material.MAGENTA_WOOL;
            case '3' -> Material.LIGHT_BLUE_WOOL;
            case '4' -> Material.YELLOW_WOOL;
            case '5' -> Material.LIME_WOOL;
            case '6' -> Material.PINK_WOOL;
            case '7' -> Material.GRAY_WOOL;
            case '8' -> Material.LIGHT_GRAY_WOOL;
            case '9' -> Material.CYAN_WOOL;
            case 'A' -> Material.PURPLE_WOOL;
            case 'B' -> Material.BLUE_WOOL;
            case 'C' -> Material.BROWN_WOOL;
            case 'D' -> Material.GREEN_WOOL;
            case 'E' -> Material.RED_WOOL;
            case 'F' -> Material.BLACK_WOOL;
            default -> Material.BEDROCK;
        };
    }

    public static char getCorrespondingChar(Material m) {
        return switch (m) {
            case WHITE_WOOL -> '0';
            case ORANGE_WOOL -> '1';
            case MAGENTA_WOOL -> '2';
            case LIGHT_BLUE_WOOL -> '3';
            case YELLOW_WOOL -> '4';
            case LIME_WOOL -> '5';
            case PINK_WOOL -> '6';
            case GRAY_WOOL -> '7';
            case LIGHT_GRAY_WOOL -> '8';
            case CYAN_WOOL -> '9';
            case PURPLE_WOOL -> 'A';
            case BLUE_WOOL -> 'B';
            case BROWN_WOOL -> 'C';
            case GREEN_WOOL -> 'D';
            case RED_WOOL -> 'E';
            case BLACK_WOOL -> 'F';
            default -> 'n';
        };
    }

    public static boolean isWoolBlock(Material m) {
        return switch (m) {
            case WHITE_WOOL, ORANGE_WOOL, BLACK_WOOL, RED_WOOL, GREEN_WOOL, BROWN_WOOL,
                 BLUE_WOOL, PURPLE_WOOL, CYAN_WOOL, LIGHT_GRAY_WOOL, GRAY_WOOL, PINK_WOOL,
                 LIME_WOOL, YELLOW_WOOL, LIGHT_BLUE_WOOL, MAGENTA_WOOL -> true;
            default -> false;
        };
    }

    public static String addValueToJSON(int value, String key, String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        JsonObject result = new JsonObject();
        result.addProperty(key, value);
        obj.entrySet().forEach(e -> result.add(e.getKey(), e.getValue()));
        return result.toString();
    }

    public static String addValueToJSON(String value, String key, String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        JsonObject result = new JsonObject();
        result.addProperty(key, value);
        obj.entrySet().forEach(e -> result.add(e.getKey(), e.getValue()));
        return result.toString();
    }

    public static int parseNextIndexTable(String metadata) {
        return Integer.parseInt(metadata.split(",")[1]);
    }

    public static int parseLastIndexTable(String metadata) {
        return Integer.parseInt(metadata.split(",")[0]);
    }

    public static int parseNextIndexRow(String metadata) {
        return Integer.parseInt(metadata.split(";")[0].split(",")[1]);
    }

    public static int parseLastIndexRow(String metadata) {
        return Integer.parseInt(metadata.split(";")[0].split(",")[0]);
    }

    public static String parseFileMime(String metadata) {
        return metadata.split(",")[3];
    }

    public static String parseTitle(String metadata) {
        return metadata.split(",")[2];
    }

    public static String parseRowContent(String metadata) {
        String[] parts = metadata.split(";", 2);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Malformed row metadata (no ';' separator): " + metadata);
        }
        return parts[1];
    }

    public static String parseTableProtectionFlags(String metadata) {
        return metadata.split(",")[3].split(":")[1].toLowerCase();
    }

    public static String fileMetadataBuilder(String title, String mime, int last, int next) {
        return last + "," + next + "," + title + "," + mime;
    }

    public static String tableMetadataBuilder(String title, int last, int next) {
        return last + "," + next + "," + title;
    }

    public static String rowBuilder(int last, int next, String content) {
        return last + "," + next + ";" + content;
    }

    public static String contextNameBuilder(String fileTitle) {
        return "/" + URLEncoder.encode(fileTitle, StandardCharsets.UTF_8);
    }

    public static boolean isValidFileMetadata(String metadata) {
        return metadata != null && !metadata.isEmpty() && metadata.split(",").length == 4;
    }

    public static boolean isValidTableMetadata(String metadata) {
        if (metadata == null || metadata.isEmpty()) return false;
        int parts = metadata.split(",").length;
        return parts == 3 || parts == 4;
    }

    public static boolean tableHasProtectionFlags(String metadata) {
        return metadata.split(",").length == 4;
    }

    public static String tableProtectionBuilder(String rules) {
        return "protected:" + rules;
    }

    public static String generateProtectedMetadata(String currentMetadata, String protectionField) {
        int last = parseLastIndexTable(currentMetadata);
        int next = parseNextIndexTable(currentMetadata);
        String title = parseTitle(currentMetadata);
        return tableMetadataBuilder(title, last, next) + "," + protectionField;
    }

    public static String hashString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            BigInteger no = new BigInteger(1, digest);
            String hash = no.toString(16);
            while (hash.length() < 128) hash = "0" + hash;
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String userRowBuilder(String username, String password) {
        JsonObject obj = new JsonObject();
        obj.addProperty("username", username);
        obj.addProperty("passHash", password);
        return obj.toString();
    }

    public static String generateAuthTokenJson() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = HexFormat.of().formatHex(tokenBytes);

        String expiration = String.valueOf(
                Instant.now().toEpochMilli() + (AUTH_TOKEN_LIFESPAN_DAYS * 24 * 60 * 60 * 1000L)
        );

        JsonObject obj = new JsonObject();
        obj.addProperty("token", token);
        obj.addProperty("expiration", expiration);
        return obj.toString();
    }

    public static boolean isExpired(String expiration) {
        long given = Long.parseLong(expiration);
        return given < Instant.now().toEpochMilli();
    }

    public static MethodResponse areValidProtectionFlags(String protection) {
        if (protection == null || protection.isEmpty() || protection.length() > 4) {
            return new MethodResponse(400,
                    "Bad Request: 1–4 protection flags required. Valid flags: c (create), r (read), u (update), d (delete), * (all)",
                    null, true);
        }
        for (char c : protection.toLowerCase().toCharArray()) {
            if (!VALID_FLAG_CHARS.contains(c)) {
                return new MethodResponse(400,
                        "Bad Request: Invalid flag '" + c + "'. Valid flags: c, r, u, d, *",
                        null, true);
            }
        }
        return new MethodResponse(200, "Flags are valid", "Flags are valid", false);
    }

    public static boolean meetsCondition(String content, String key, String target) {
        try {
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            return obj.has(key) && obj.get(key).getAsString().equals(target);
        } catch (Exception e) {
            return false;
        }
    }

    public static MethodResponse filterJsonArray(String json, String key, String target) {
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            JsonArray filtered = new JsonArray();
            for (int i = 0; i < arr.size(); i++) {
                String obj = arr.get(i).getAsString();
                if (meetsCondition(obj, key, target)) {
                    filtered.add(arr.get(i));
                }
            }
            return new MethodResponse(200, "OK", filtered.toString(), false);
        } catch (Exception e) {
            e.printStackTrace();
            return new MethodResponse(500, "Internal Server Error: " + e.getMessage(), "[]", true);
        }
    }
}