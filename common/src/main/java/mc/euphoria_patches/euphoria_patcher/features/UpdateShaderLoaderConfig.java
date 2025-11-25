package mc.euphoria_patches.euphoria_patcher.features;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.logging.EuphoriaLogger;
import mc.euphoria_patches.euphoria_patcher.integration.iris.IrisReloadManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class UpdateShaderLoaderConfig {

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[UpdateShaderLoaderConfig] " + message);
    }

    public static Path getShaderLoaderPath(){
        Path shaderLoaderConfig = EuphoriaPatcher.configDirectory.resolve("iris.properties");
        if(!Files.exists(shaderLoaderConfig)) shaderLoaderConfig = EuphoriaPatcher.configDirectory.resolve("oculus.properties");
        if(!Files.exists(shaderLoaderConfig)) shaderLoaderConfig = EuphoriaPatcher.shaderpacks.getParent().resolve("optionsshaders.txt");
        if (!Files.exists(shaderLoaderConfig)) shaderLoaderConfig = null;
        return shaderLoaderConfig;
    }

    public static void updateShaderLoaderConfig(boolean styleUnbound, boolean styleReimagined) {
        debugLog("Starting updateShaderLoaderConfig - Unbound: " + styleUnbound + ", Reimagined: " + styleReimagined);
        
        Path shaderLoaderConfig = getShaderLoaderPath();
        if (shaderLoaderConfig == null) {
            debugLog("No shader loader config found");
            EuphoriaPatcher.log(0, "No shader loader config found");
            return;
        }
        debugLog("Found shader loader config at: " + shaderLoaderConfig);

        String shaderLoaderName = shaderLoaderConfig.toString().contains("iris") ? "iris.properties" : 
                                 shaderLoaderConfig.toString().contains("oculus") ? "oculus.properties" : 
                                 "OptiFine's optionsshaders.txt";
        debugLog("Identified shader loader: " + shaderLoaderName);

        File fileToBeModified = shaderLoaderConfig.toFile();
        StringBuilder oldContent = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileToBeModified))) {
            debugLog("Reading shader loader config file");
            String line;
            while ((line = reader.readLine()) != null) {
                oldContent.append(line).append(System.lineSeparator());
            }
            debugLog("Successfully read shader loader config file");

            boolean hasPatchName = oldContent.toString().contains(EuphoriaPatcher.PATCH_NAME);
            boolean hasPatchVersion = oldContent.toString().contains(EuphoriaPatcher.PATCH_VERSION);
            debugLog("Config contains patch name: " + hasPatchName + ", contains patch version: " + hasPatchVersion);

            if (hasPatchName && !hasPatchVersion) {
                debugLog("Need to update shader config - found old version reference");
                String newContent = setNewShaderLoaderSelectedPackName(oldContent, styleUnbound, styleReimagined);
                debugLog("Generated new content for config file");

                try (FileWriter writer = new FileWriter(fileToBeModified)) {
                    debugLog("Writing updated content to config file");
                    writer.write(newContent);
                    debugLog("Successfully wrote updated content");
                } catch (IOException e) {
                    debugLog("Error writing to config file: " + e.getMessage());
                    EuphoriaPatcher.log(3,0, "Error writing to " + shaderLoaderName + " config file: " + e.getMessage());
                    return;
                }
                
                String oldPack = oldContent.toString().contains("shaderPack=") ? 
                    oldContent.toString().split("shaderPack=")[1].split("\n")[0].trim() : "unknown";
                String newPack = newContent.contains("shaderPack=") ? 
                    newContent.split("shaderPack=")[1].split("\n")[0].trim() : "unknown";
                debugLog("Updated shader pack reference from '" + oldPack + "' to '" + newPack + "'");
                    
                EuphoriaPatcher.log(0, "Successfully applied new version in " + shaderLoaderName + " config file!");
                EuphoriaPatcher.log(0, oldPack + " -> " + newPack);
            } else {
                debugLog("No update needed for shader config");
            }
        } catch (IOException e) {
            debugLog("Error accessing shader loader config file: " + e.getMessage());
            EuphoriaPatcher.log(3,0, "Error reading or writing to " + shaderLoaderName + " config file: " + e.getMessage());
            return;
        }

        debugLog("Attempting to schedule shader reload");
        IrisReloadManager.findAndScheduleReload();
    }

    private static String setNewShaderLoaderSelectedPackName(StringBuilder oldContent, boolean styleUnbound, boolean styleReimagined) {
        String style = styleUnbound ? "Unbound" : "Reimagined";
        if (styleUnbound && styleReimagined) { // Both styles installed
            style = oldContent.toString().contains(EuphoriaPatcher.PATCH_NAME) && !oldContent.toString().contains(EuphoriaPatcher.PATCH_VERSION) && oldContent.toString().contains("Unbound") ? "Unbound" : "Reimagined";
        }
        String newName = EuphoriaPatcher.BRAND_NAME + style + EuphoriaPatcher.VERSION + " + " + EuphoriaPatcher.PATCH_NAME + EuphoriaPatcher.PATCH_VERSION;
        return oldContent.toString().replaceAll("shaderPack=.*", "shaderPack=" + newName);
    }
    
    /**
     * Gets the path to the currently selected shaderpack
     * @return Path to the current shaderpack directory or zip file, or null if none is selected or an error occurs
     */
    public static Path getCurrentShaderpackPath() {
        Path shaderLoaderConfig = getShaderLoaderPath();
        if (shaderLoaderConfig == null) {
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(shaderLoaderConfig.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("shaderPack=")) {
                    String shaderpackName = line.substring("shaderPack=".length()).trim();
                    
                    // Check if no shaderpack is selected (empty or "OFF")
                    if (shaderpackName.isEmpty() || shaderpackName.equalsIgnoreCase("OFF")) {
                        return null;
                    }
                    
                    // Find the actual shader file by name without relying on direct path resolution
                    try {
                        return findShaderpackByName(shaderpackName);
                    } catch (Exception e) {
                        debugLog("Error finding shaderpack: " + e.getMessage());
                        EuphoriaPatcher.log(2, 0, "Could not find shaderpack: " + shaderpackName + " - " + e.getMessage());
                        return null;
                    }
                }
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(3, 0, "Error reading shader loader config: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Properly normalizes a shader name by removing special characters
     * @param name The shader name to normalize
     * @return The normalized shader name
     */
    private static String normalizeShaderName(String name) {
        // First handle the § character specifically
        String withoutSection = name.replace("§", "").replace("\\u00A7", "");
        
        // Then handle other special characters
        String safeChars = withoutSection.replaceAll("[^a-zA-Z0-9_ \\-.+()\\[\\]{}]", "");
        
        if (!name.equals(safeChars)) {
            debugLog("Normalized '" + name + "' to '" + safeChars + "'");
        }
        return safeChars.trim();
    }

    private static Path findShaderpackByName(String shaderpackName) throws IOException {
        // First try direct resolution (will work for normal filenames)
        try {
            Path directPath = EuphoriaPatcher.shaderpacks.resolve(shaderpackName);
            if (Files.exists(directPath)) {
                debugLog("Found shader directly: " + directPath);
                return directPath;
            }
        } catch (InvalidPathException e) {
            debugLog("Invalid path characters in shader name: " + e.getMessage());
        }
        
        debugLog("Direct path resolution failed for: " + shaderpackName + ", trying directory scan");
        
        // If direct resolution fails (likely due to special characters), list files and find match
        String normalizedName = normalizeShaderName(shaderpackName);
        debugLog("Normalized shader name: " + normalizedName);
        
        // Also try the special case for our error shader
        final boolean isErrorShader = shaderpackName.contains("EuphoriaPatches") && 
                                     shaderpackName.contains("Error") && 
                                     shaderpackName.contains("Shader");
        
        try (Stream<Path> fileStream = Files.list(EuphoriaPatcher.shaderpacks)) {
            // Try to find a file that matches when normalized
            Path result = fileStream
                    .filter(Files::exists)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        
                        // Check exact match first
                        if (fileName.equals(shaderpackName)) {
                            debugLog("Found exact shader match: " + fileName);
                            return true;
                        }
                        
                        // Special handling for error shader
                        if (isErrorShader && fileName.contains("EuphoriaPatches") && 
                            fileName.contains("Error") && fileName.contains("Shader")) {
                            debugLog("Found error shader: " + fileName);
                            return true;
                        }
                        
                        // Try normalized match
                        String normalizedFileName = normalizeShaderName(fileName);
                        boolean matches = normalizedFileName.equals(normalizedName);
                        if (matches) {
                            debugLog("Found matching shader via normalization: " + fileName);
                        }
                        return matches;
                    })
                    .findFirst().orElse(null);

            if (result == null) {
                debugLog("No matching shader found in directory scan");
            }
            return result;
        }
    }
}
