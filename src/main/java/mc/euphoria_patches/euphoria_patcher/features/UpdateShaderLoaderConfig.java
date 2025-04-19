package mc.euphoria_patches.euphoria_patcher.features;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.util.EuphoriaLogger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class UpdateShaderLoaderConfig {

    private static volatile boolean pendingReload = false;
    private static volatile Class<?> pendingIrisClass = null;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[UpdateShaderLoaderConfig] " + message);
    }

    private static Path getShaderLoaderPath(){
        Path shaderLoaderConfig = EuphoriaPatcher.configDirectory.resolve("iris.properties");
        if(!Files.exists(shaderLoaderConfig)) shaderLoaderConfig = EuphoriaPatcher.configDirectory.resolve("oculus.properties");
        if(!Files.exists(shaderLoaderConfig)) shaderLoaderConfig = EuphoriaPatcher.shaderpacks.getParent().resolve("optionsshaders.txt");
        if (!Files.exists(shaderLoaderConfig)) shaderLoaderConfig = null;
        return shaderLoaderConfig;
    }

    public static void checkPendingReload() {
        if (pendingReload && pendingIrisClass != null) {
            try {
                debugLog("Processing pending shader reload on main thread");
                pendingIrisClass.getMethod("reload").invoke(null);
                debugLog("Successfully reloaded shaders");
            } catch (Exception e) {
                EuphoriaPatcher.log(2, 0, "Error reloading Iris shaders:" + e.getMessage());
            } finally {
                pendingReload = false;
                pendingIrisClass = null;
            }
        }
    }

    public static void updateShaderLoaderConfig(boolean styleUnbound, boolean styleReimagined) {
        Path shaderLoaderConfig = getShaderLoaderPath();
        if (shaderLoaderConfig == null) {
            EuphoriaPatcher.log(0, "No shader loader config found");
            return;
        }

        String shaderLoaderName = shaderLoaderConfig.toString().contains("iris") ? "iris.properties" : shaderLoaderConfig.toString().contains("oculus") ? "oculus.properties" : "OptiFine's optionsshaders.txt";

        File fileToBeModified = shaderLoaderConfig.toFile();
        StringBuilder oldContent = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileToBeModified))) {
            String line;
            while ((line = reader.readLine()) != null) {
                oldContent.append(line).append(System.lineSeparator());
            }

            if (oldContent.toString().contains(EuphoriaPatcher.PATCH_NAME) && !oldContent.toString().contains(EuphoriaPatcher.PATCH_VERSION)) {
                String newContent = setNewShaderLoaderSelectedPackName(oldContent, styleUnbound, styleReimagined);

                try (FileWriter writer = new FileWriter(fileToBeModified)) {
                    writer.write(newContent);
                } catch (IOException e) {
                    EuphoriaPatcher.log(3,0, "Error writing to " + shaderLoaderName + " config file: " + e.getMessage());
                }
                EuphoriaPatcher.log(0, "Successfully applied new version in " + shaderLoaderName + " config file!");
                EuphoriaPatcher.log(0, oldContent.toString().split("shaderPack=")[1] + " -> " + newContent.split("shaderPack=")[1]);
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(3,0, "Error reading or writing to " + shaderLoaderName + " config file: " + e.getMessage());
        }

        Class<?> irisClass;

        // Try both possible Iris class locations
        try {
            irisClass = Class.forName("net.irisshaders.iris.Iris");
            debugLog("Found Iris class at net.irisshaders.iris.Iris");
        } catch (ClassNotFoundException e1) {
            try {
                irisClass = Class.forName("net.coderbot.iris.Iris");
                debugLog("Found Iris class at net.coderbot.iris.Iris");
            } catch (ClassNotFoundException e2) {
                // Iris isn't installed, this is fine - just log to debug
                debugLog("Iris not found - this is normal if Iris isn't installed");
                return;
            }
        }
        
        debugLog("Scheduling shader reload on next game tick");
        pendingIrisClass = irisClass;
        pendingReload = true;
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
                    
                    Path shaderpackPath;
                    // If the name ends with .zip, it's a zip file
                    if (shaderpackName.endsWith(".zip")) {
                        shaderpackPath = EuphoriaPatcher.shaderpacks.resolve(shaderpackName);
                    } else {
                        // Otherwise it's a directory
                        shaderpackPath = EuphoriaPatcher.shaderpacks.resolve(shaderpackName);
                    }
                    
                    // Verify the path exists before returning
                    if (Files.exists(shaderpackPath)) {
                        return shaderpackPath;
                    } else {
                        EuphoriaPatcher.log(2,0, "Shaderpack specified in config doesn't exist: " + shaderpackPath);
                        return null;
                    }
                }
            }
        } catch (IOException e) {
            EuphoriaPatcher.log(3,0, "Error reading shader loader config: " + e.getMessage());
        }
        
        return null;
    }
}
