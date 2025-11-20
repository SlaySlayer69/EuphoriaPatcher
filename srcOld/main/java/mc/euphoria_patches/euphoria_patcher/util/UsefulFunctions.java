package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class UsefulFunctions {
    public static void deleteRecursively(Path path) throws IOException { // needed to delete folders
        if (Files.isDirectory(path)) {
            try (Stream<Path> entries = Files.list(path)) {
                entries.forEach(entry -> {
                    try {
                        deleteRecursively(entry);
                    } catch (IOException e) {
                        EuphoriaPatcher.log(2, 0, "Error deleting entry: " + entry + " - " + e.getMessage());
                    }
                });
            }
        }
        Files.delete(path);
    }
}
