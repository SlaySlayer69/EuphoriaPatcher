package com.euphoriapatches.euphoria_patcher.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileOperations {

    /**
     * Read a file's content as a string
     * @param filePath Path to the file
     * @return The file content as a String, or null if an error occurs
     */
    public static String readFileAsString(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            return null;
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        } catch (IOException e) {
            return null;
        }

        return content.toString();
    }
}
