package mc.euphoria_patches.euphoria_patcher.features;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ModifyPatchedShaderpacks {

    /**
     * Modifies files in shader packs based on specified path and file extension
     *
     * @param patchedFile The shader pack file or directory
     * @param styleUnbound Whether to include Unbound style
     * @param styleReimagined Whether to include Reimagined style
     * @param targetPath Path relative to shader pack that should be modified (file or directory)
     * @param fileExtension File extension filter for directory paths (e.g. ".lang"), null for single files
     * @param regexAndReplacements Pairs of regex patterns and their replacements
     * @throws IOException If an I/O error occurs
     */
    public static void modifyFiles(Path patchedFile, boolean styleUnbound, boolean styleReimagined,
                                   String targetPath, String fileExtension, String... regexAndReplacements) throws IOException {
        if (regexAndReplacements.length % 2 != 0) {
            EuphoriaPatcher.log(2, 0, "Regex and replacement pairs must be provided");
            return;
        }

        processShaderPacks(patchedFile, styleUnbound, styleReimagined, shaderPack -> {
            try {
                Path resolvedPath = shaderPack.resolve(targetPath);

                if (fileExtension != null && Files.isDirectory(resolvedPath)) {
                    // Process directory with file extension filter
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(resolvedPath, "*" + fileExtension)) {
                        for (Path file : files) {
                            modifyFile(file, regexAndReplacements);
                        }
                    }
                } else {
                    // Process single file
                    modifyFile(resolvedPath, regexAndReplacements);
                }
            } catch (IOException e) {
                EuphoriaPatcher.log(2, 0, "Error processing files in " + shaderPack.getFileName() + ": " + e.getMessage());
            }
        });
    }

    private static void modifyFile(Path filePath, String... regexAndReplacements) throws IOException {
        String content = new String(Files.readAllBytes(filePath));
        String modifiedContent = applyReplacements(content, regexAndReplacements);
        Files.write(filePath, modifiedContent.getBytes());
    }

    private static void processShaderPacks(Path patchedFile, boolean styleUnbound, boolean styleReimagined, Consumer<Path> processor) {
        List<Path> shaderPacks = getShaderPacks(patchedFile, styleUnbound, styleReimagined);
        for (Path shaderPack : shaderPacks) {
            if (Files.exists(shaderPack)) {
                processor.accept(shaderPack);
            }
        }
    }

    private static String applyReplacements(String content, String... regexAndReplacements) {
        String modifiedContent = content;
        for (int i = 0; i < regexAndReplacements.length; i += 2) {
            String regex = regexAndReplacements[i];
            String replacement = regexAndReplacements[i + 1];
            modifiedContent = modifiedContent.replaceAll(regex, replacement);
        }
        return modifiedContent;
    }

    private static List<Path> getShaderPacks(Path patchedFile, boolean styleUnbound, boolean styleReimagined) {
        List<Path> shaderPacks = new ArrayList<>();
        shaderPacks.add(patchedFile);

        if (styleUnbound && styleReimagined) {
            Path otherStylePath = patchedFile.getFileName().toString().contains("Reimagined")
                    ? patchedFile.resolveSibling(patchedFile.getFileName().toString().replace("Reimagined", "Unbound"))
                    : patchedFile.resolveSibling(patchedFile.getFileName().toString().replace("Unbound", "Reimagined"));

            if (Files.exists(otherStylePath)) {
                shaderPacks.add(otherStylePath);
            }
        }
        return shaderPacks;
    }
}