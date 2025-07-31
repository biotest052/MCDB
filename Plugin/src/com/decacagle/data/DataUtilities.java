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
import java.time.Instant;

public final class DataUtilities {

    private static long authTokenLifespanDays = 7;

    public static String asciiToHex(char c) {
        return Integer.toHexString((int) (c));
    }

    /**
     * Takes hexadecimal value as a string (hex) and returns the corresponding ASCII char
     */
    public static char hexToAscii(String hex) {
        return ((char) (Integer.parseInt(hex, 16)));
    }

    /**
     * Takes char (inputC) and returns the corresponding Material for encoding or whatever
     * Returns null if given invalid char
     */
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

    /**
     * Takes Material (m) and returns the corresponding char for encoding or whatever
     * Returns 'n' if given invalid Material
     */
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
            case WHITE_WOOL, ORANGE_WOOL, BLACK_WOOL, RED_WOOL, GREEN_WOOL, BROWN_WOOL, BLUE_WOOL, PURPLE_WOOL,
                 CYAN_WOOL, LIGHT_GRAY_WOOL, GRAY_WOOL, PINK_WOOL, LIME_WOOL, YELLOW_WOOL, LIGHT_BLUE_WOOL,
                 MAGENTA_WOOL -> true;
            default -> false;
        };

    }

    public static String addValueToJSON(int value, String key, String JSON) {
        return "{\""+key+"\":" + value + "," + JSON.substring(1);
    }

    public static String addValueToJSON(String value, String key, String JSON) {
        return "{\""+key+"\":\"" + value + "\"," + JSON.substring(1);
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
        return metadata.split(";")[1];
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
        String urlEncodedFileTitle = URLEncoder.encode(fileTitle, StandardCharsets.UTF_8);
        return "/" + urlEncodedFileTitle;
    }

    public static boolean isValidFileMetadata(String metadata) {
        return !metadata.isEmpty() && metadata.split(",").length == 4;
    }

    public static boolean isValidTableMetadata(String metadata) {
        return !metadata.isEmpty() && (metadata.split(",").length == 3 || metadata.split(",").length == 4);
    }

    public static String hashString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] messageDigest = md.digest(input.getBytes());

            BigInteger no = new BigInteger(1, messageDigest);

            String hashtext = no.toString(16);

            while (hashtext.length() < 128) {
                hashtext = "0" + hashtext;
            }

            return hashtext;
        }

        // For specifying wrong message digest algorithms
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String userRowBuilder(String username, String password) {
        return "{\"username\":\"" + username + "\", \"passHash\":\"" + password + "\"}";
    }

    public static String generateAuthTokenJson() {
        double random = Math.random() * 10000;
        String token = hashString("" + random);
        String expiration = "" + (Instant.now().toEpochMilli() + (authTokenLifespanDays * 24 * 60 * 60 * 1000));
        return "{\"token\":\"" + token + "\",\"expiration\":\"" + expiration + "\"}";
    }

    public static boolean meetsCondition(String content, String key, String target) {
        JsonObject obj = JsonParser.parseString(content).getAsJsonObject();

        if (obj.has(key)) {
            String keyValue = obj.get(key).getAsString();
            return keyValue.equals(target);
        }

        return false;

    }

    /**
     * Takes an array of JSON objects as a String and returns a new list that only contains the objects where the object's key == target
     */
    public static MethodResponse filterJsonArray(String json, String key, String target) {
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();

            for (int i = 0; i < arr.size(); i++) {
                String obj = arr.get(i).getAsString();

                if (!meetsCondition(obj, key, target)) {
                    arr.remove(i);
                    i--;
                }

            }

            return new MethodResponse(200, arr.getAsString(), arr.getAsString(), false);
        } catch (Exception e) {
            e.printStackTrace();
            return new MethodResponse(500, "Internal Server Error: " + e.getMessage(), "[]", true);
        }
    }

    public static boolean isExpired(String expiration) {
        long given = Long.parseLong(expiration);
        long now = Instant.now().toEpochMilli();
        return given < now;
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

    public static MethodResponse areValidProtectionFlags(String protection) {
        if (protection.length() == 1) {
            if (protection.toLowerCase().indexOf('c') == -1 && protection.toLowerCase().indexOf('r') == -1 && protection.toLowerCase().indexOf('u') == -1 && protection.toLowerCase().indexOf('d') == -1 && protection.toLowerCase().indexOf('*') == -1) {
                // if given flag is not one of the three available flags
                return new MethodResponse(400, "Bad Request: Invalid protection flags. Valid flags are: C - Create access, R - Read access, U - Update access, D - Delete access, and * - All access (Create, Read, Update, and Delete)", null, true);
            }
        } else if (protection.length() == 2) {
            char char1 = protection.toLowerCase().charAt(0);
            char char2 = protection.toLowerCase().charAt(1);
            if (char1 != 'c' && char1 != 'r' && char1 != 'u' && char1 != 'd' && char1 != '*') {
                // if given flag is not one of the three available flags
                return new MethodResponse(400, "Bad Request: Invalid protection flags. Valid flags are: C - Create access, R - Read access, U - Update access, D - Delete access, and * - All access (Create, Read, Update, and Delete)", null, true);
            }
            if (char2 != 'c' && char2 != 'r' && char2 != 'u' && char2 != 'd' && char2 != '*') {
                // if given flag is not one of the three available flags
                return new MethodResponse(400, "Bad Request: Invalid protection flags. Valid flags are: C - Create access, R - Read access, U - Update access, D - Delete access, and * - All access (Create, Read, Update, and Delete)", null, true);
            }
        } else if (protection.length() == 3) {
            char char1 = protection.toLowerCase().charAt(0);
            char char2 = protection.toLowerCase().charAt(1);
            char char3 = protection.toLowerCase().charAt(2);
            if (char1 != 'c' && char1 != 'r' && char1 != 'u' && char1 != 'd' && char1 != '*') {
                // if given flag is not one of the three available flags
                return new MethodResponse(400, "Bad Request: Invalid protection flags. Valid flags are: C - Create access, R - Read access, U - Update access, D - Delete access, and * - All access (Create, Read, Update, and Delete)", null, true);
            }
            if (char2 != 'c' && char2 != 'r' && char2 != 'u' && char2 != 'd' && char2 != '*') {
                // if given flag is not one of the three available flags
                return new MethodResponse(400, "Bad Request: Invalid protection flags. Valid flags are: C - Create access, R - Read access, U - Update access, D - Delete access, and * - All access (Create, Read, Update, and Delete)", null, true);
            }
            if (char3 != 'c' && char3 != 'r' && char3 != 'u' && char3 != 'd' && char3 != '*') {
                // if given flag is not one of the three available flags
                return new MethodResponse(400, "Bad Request: Invalid protection flags. Valid flags are: C - Create access, R - Read access, U - Update access, D - Delete access, and * - All access (Create, Read, Update, and Delete)", null, true);
            }
        } else if (protection.length() == 4) {
            char char1 = protection.toLowerCase().charAt(0);
            char char2 = protection.toLowerCase().charAt(1);
            char char3 = protection.toLowerCase().charAt(2);
            char char4 = protection.toLowerCase().charAt(3);
            if (char1 != 'c' && char1 != 'r' && char1 != 'u' && char1 != 'd' && char1 != '*') {
                // if given flag is not one of the three available flags
                return new MethodResponse(400, "Bad Request: Invalid protection flags. Valid flags are: C - Create access, R - Read access, U - Update access, D - Delete access, and * - All access (Create, Read, Update, and Delete)", null, true);
            }
            if (char2 != 'c' && char2 != 'r' && char2 != 'u' && char2 != 'd' && char2 != '*') {
                // if given flag is not one of the three available flags
                return new MethodResponse(400, "Bad Request: Invalid protection flags. Valid flags are: C - Create access, R - Read access, U - Update access, D - Delete access, and * - All access (Create, Read, Update, and Delete)", null, true);
            }
            if (char3 != 'c' && char3 != 'r' && char3 != 'u' && char3 != 'd' && char3 != '*') {
                // if given flag is not one of the three available flags
                return new MethodResponse(400, "Bad Request: Invalid protection flags. Valid flags are: C - Create access, R - Read access, U - Update access, D - Delete access, and * - All access (Create, Read, Update, and Delete)", null, true);
            }
            if (char4 != 'c' && char4 != 'r' && char4 != 'u' && char4 != 'd' && char4 != '*') {
                // if given flag is not one of the three available flags
                return new MethodResponse(400, "Bad Request: Invalid protection flags. Valid flags are: C - Create access, R - Read access, U - Update access, D - Delete access, and * - All access (Create, Read, Update, and Delete)", null, true);
            }
        } else {
            // too many or too little flags given
            return new MethodResponse(400, "Bad Request: Invalid quantity of protection flags. You can have 1 flag at minimum, and a maximum of 4. Valid flags are: C - Create access, R - Read access, U - Update access, D - Delete access, and * - All access (Create, Read, Update, and Delete)", null, true);
        }
        return new MethodResponse(200, "Flags are valid", "Flags are valid", false);
    }

    public static boolean tableHasProtectionFlags(String metadata) {
        return metadata.split(",").length == 4;
    }

}
