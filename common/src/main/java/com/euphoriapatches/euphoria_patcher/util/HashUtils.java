package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for cryptographic hashing operations
 */
public class HashUtils {

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[HashUtils] " + message);
    }

    /**
     * Calculates the SHA-256 hash of a file
     * @param filePath Path to the file to hash
     * @return Hexadecimal string representation of the hash, or null if an error occurs
     */
    public static String calculateSHA256(Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = digest.digest(fileBytes);

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            debugLog("Error calculating SHA-256: " + e.getMessage());
            return null;
        }
    }

    /**
     * Verifies that a file's SHA-256 hash matches an expected hash
     * @param filePath Path to the file to verify
     * @param expectedHash The expected SHA-256 hash in hexadecimal format
     * @return true if the hash matches, false otherwise
     */
    public static boolean verifyHash(Path filePath, String expectedHash) {
        String actualHash = calculateSHA256(filePath);
        if (actualHash == null) {
            return false;
        }

        boolean hashMatches = expectedHash.equals(actualHash);
        debugLog("Hash verification for " + filePath.getFileName());
        debugLog("  Expected: " + expectedHash);
        debugLog("  Actual:   " + actualHash);
        debugLog("  Match:    " + hashMatches);

        return hashMatches;
    }

    /**
     * Checks if a file's hash does NOT match the expected hash
     * @param filePath Path to the file to check
     * @param expectedHash The expected SHA-256 hash in hexadecimal format
     * @return true if the hash does NOT match (incorrect), false if it matches or if an error occurs
     */
    public static boolean hasIncorrectHash(Path filePath, String expectedHash) {
        return !verifyHash(filePath, expectedHash);
    }
}
