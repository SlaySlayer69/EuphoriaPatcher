package mc.euphoria_patches.euphoria_patcher.logging;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
import mc.euphoria_patches.euphoria_patcher.integration.ShaderLoader;
import mc.euphoria_patches.euphoria_patcher.integration.iris.IrisReloadManager;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates an error shader that displays error messages in-game
 */
public class ErrorShaderGenerator {
    
    private static final String ERROR_SHADER_FOLDER = "_0EuphoriaPatches_ErrorShader";
    private static final String ERROR_TEXTS_FILE = "shaders/errorTexts.glsl";
    private static final int MAX_LINE_LENGTH = 90;
    
    // Character mappings for GLSL conversion
    private static final Map<Character, String> CHAR_TO_GLSL = new HashMap<>();
    
    static {
        // Initialize character mappings
        CHAR_TO_GLSL.put(' ', "_space");
        CHAR_TO_GLSL.put('_', "_under");
        CHAR_TO_GLSL.put('"', "_quote");
        CHAR_TO_GLSL.put('!', "_exclm");
        CHAR_TO_GLSL.put('>', "_gt");
        CHAR_TO_GLSL.put('<', "_lt");
        CHAR_TO_GLSL.put('[', "_opsqr");
        CHAR_TO_GLSL.put(']', "_clsqr");
        CHAR_TO_GLSL.put('(', "_opprn");
        CHAR_TO_GLSL.put(')', "_clprn");
        CHAR_TO_GLSL.put('█', "_block");
        CHAR_TO_GLSL.put('©', "_copyr");
        CHAR_TO_GLSL.put('=', "_equal");
        CHAR_TO_GLSL.put('+', "_plus");
        CHAR_TO_GLSL.put('.', "_dot");
        CHAR_TO_GLSL.put('-', "_minus");
        CHAR_TO_GLSL.put(',', "_comma");
        CHAR_TO_GLSL.put(':', "_colon");
        CHAR_TO_GLSL.put('/', "_slash");
    }
    
    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ErrorShaderGenerator] " + message);
    }
    
    private static final int MAX_ERROR_MESSAGES = 30;
    
    /**
     * Generates an error shader with the given error messages
     *
     * @param errorMessages List of error messages to display
     */
    public static void generateErrorShader(List<String> errorMessages) {
        try {
            // Limit error messages to prevent memory issues and infinite loops
            if (errorMessages.size() > MAX_ERROR_MESSAGES) {
                debugLog("Too many errors (" + errorMessages.size() + "), limiting to first " + MAX_ERROR_MESSAGES);
                errorMessages = new ArrayList<>(errorMessages.subList(0, MAX_ERROR_MESSAGES));
                errorMessages.add("");
                errorMessages.add("... and " + (errorMessages.size() - MAX_ERROR_MESSAGES) + " more errors (truncated to prevent overflow)");
            }
            
            debugLog("Generating error shader with " + errorMessages.size() + " messages");
            
            // Create target directory in shaderpacks
            Path targetDir = EuphoriaPatcher.shaderpacks.resolve(ERROR_SHADER_FOLDER);
            
            // Check if the error shader is currently selected
            boolean isErrorShaderActive = isErrorShaderActive();
            debugLog("Error shader is " + (isErrorShaderActive ? "active" : "not active"));
            
            // Ensure the target directory exists
            if (Files.exists(targetDir)) {
                // No need to delete, just update the file
                debugLog("Error shader directory already exists, updating files");
            } else {
                debugLog("Creating new error shader directory");
                Files.createDirectories(targetDir);
                
                // Copy error shader folder to shaderpacks
                boolean copySuccess = copyErrorShaderFolder(targetDir);
                if (!copySuccess) {
                    EuphoriaPatcher.log(2, "Failed to copy error shader folder");
                    return;
                }
            }
            
            // Generate GLSL error text
            String glslErrorText = generateGLSLErrorText(errorMessages);
            
            // Update errorTexts.glsl
            Path errorTextsPath = targetDir.resolve(ERROR_TEXTS_FILE);
            if (!Files.exists(errorTextsPath.getParent())) {
                Files.createDirectories(errorTextsPath.getParent());
            }
            
            Files.write(errorTextsPath, glslErrorText.getBytes(StandardCharsets.UTF_8));
            debugLog("Successfully updated errorTexts.glsl");
            
            // Schedule a shader reload if the error shader is currently active
            if (isErrorShaderActive) {
                debugLog("Scheduling shader reload since error shader is active");
                scheduleShaderReload();
            }

        } catch (IOException e) {
            debugLog("Exception generating error shader: " + e.getMessage());
            EuphoriaPatcher.log(3, "Error generating error shader: " + e.getMessage());
        }
    }
    
    /**
     * Checks if the error shader is currently active
     */
    private static boolean isErrorShaderActive() {
        Path currentShaderPath = ShaderLoader.getCurrentShaderpackPath();
        if (currentShaderPath == null) {
            return false;
        }
        
        String shaderName = currentShaderPath.getFileName().toString();
        return shaderName.contains("EuphoriaPatches Error Shader");
    }
    
    /**
     * Schedules a shader reload on the main thread
     */
    private static void scheduleShaderReload() {
        debugLog("Scheduling shader reload for error shader updates");
        IrisReloadManager.findAndScheduleReload();
    }
    
    /**
     * Copies the error shader folder from resources to the target directory
     * @param targetDir The target directory to copy to
     * @return True if successful, false otherwise
     */
    private static boolean copyErrorShaderFolder(Path targetDir) {
        try {
            // Get the resource from classpath
            String resourcePath = ERROR_SHADER_FOLDER;
            
            // Try to copy from development environment first
            Path sourcePath = Paths.get("src/main/resources/" + resourcePath);
            if (Files.exists(sourcePath)) {
                FileUtils.copyDirectory(sourcePath.toFile(), targetDir.toFile());
                return true;
            }
            
            // Otherwise, copy files from the JAR
            ClassLoader classLoader = ErrorShaderGenerator.class.getClassLoader();
            
            // List of files to copy - expand this list to include all files
            String[] filesToCopy = {
                "shaders/composite.fsh",
                "shaders/composite.vsh",
                "shaders/dh_terrain.fsh",
                "shaders/dh_terrain.vsh",
                "shaders/dh_water.fsh",
                "shaders/dh_water.vsh",
                "shaders/errorTexts.glsl",
                "shaders/euphoria_patches.png",
                "shaders/euphoria_patches.png.mcmeta",
                "shaders/gbuffers_armor_glint.fsh",
                "shaders/gbuffers_armor_glint.vsh",
                "shaders/gbuffers_basic.fsh",
                "shaders/gbuffers_basic.vsh",
                "shaders/gbuffers_beaconbeam.fsh",
                "shaders/gbuffers_beaconbeam.vsh",
                "shaders/gbuffers_clouds.fsh",
                "shaders/gbuffers_clouds.vsh",
                "shaders/gbuffers_entities.fsh",
                "shaders/gbuffers_entities.vsh",
                "shaders/gbuffers_hand.fsh",
                "shaders/gbuffers_hand.vsh",
                "shaders/gbuffers_skybasic.fsh",
                "shaders/gbuffers_skybasic.vsh",
                "shaders/gbuffers_skytextured.fsh",
                "shaders/gbuffers_skytextured.vsh",
                "shaders/gbuffers_terrain.fsh",
                "shaders/gbuffers_terrain.vsh",
                "shaders/gbuffers_textured.fsh",
                "shaders/gbuffers_textured.vsh",
                "shaders/gbuffers_weather.fsh",
                "shaders/gbuffers_weather.vsh",
                "shaders/pack.json",
                "shaders/shaders.properties",
                "shaders/textRenderer.glsl",
                "shaders/warning.png"
            };
            
            for (String file : filesToCopy) {
                InputStream is = classLoader.getResourceAsStream(resourcePath + "/" + file);
                if (is != null) {
                    Path targetFile = targetDir.resolve(file);
                    Files.createDirectories(targetFile.getParent());
                    Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    is.close();
                } else {
                    debugLog("Could not find resource: " + resourcePath + "/" + file);
                }
            }
            
            return true;
        } catch (IOException e) {
            debugLog("Error copying error shader folder: " + e.getMessage());
            EuphoriaPatcher.log(2, "Error copying error shader folder: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Generates GLSL error text from the given error messages
     * @param messages List of error messages
     * @return Generated GLSL code
     */
    private static String generateGLSLErrorText(List<String> messages) {
        StringBuilder glsl = new StringBuilder();
        
        for (int i = 0; i < messages.size(); i++) {
            String message = messages.get(i);
            
            // Clean prefix if present
            if (message.startsWith("EuphoriaPatcher: ")) {
                message = message.substring("EuphoriaPatcher: ".length());
            }

            // Handle empty lines specially
            if (message.trim().isEmpty()) {
                glsl.append("beginTextError(3, offset);\n");
                glsl.append("    printLine();\n");
                glsl.append("endText(textColor);\n");
                glsl.append("offset.y += 1;\n\n");
                continue;
            }
            
            // Split message into lines
            List<String> lines = splitTextIntoLines(message);
            
            // Begin text block
            glsl.append("beginTextError(3, offset);\n");
            
            // Add each line
            for (int j = 0; j < lines.size(); j++) {
                String line = lines.get(j);
                String[] chars = convertTextToGLSLChars(line);
                glsl.append("    printString((").append(String.join(", ", chars)).append("));\n");
                
                // Add printLine() if not the last line
                if (j < lines.size() - 1) {
                    glsl.append("    printLine();\n");
                }
            }
            
            // End text block
            glsl.append("endText(textColor);\n\n");
            
            // Add spacing between messages
            if (i < messages.size() - 1) {
                // Use exactly the number of lines for the offset
                glsl.append("offset.y += ").append(lines.size()).append(";\n\n");
            }
        }
        
        return glsl.toString();
    }
    
    /**
     * Splits text into lines at word boundaries, respecting maximum line length
     * @param text Text to split
     * @return List of lines
     */
    private static List<String> splitTextIntoLines(String text) {
        List<String> lines = new ArrayList<>();
        
        // If text is shorter than threshold, just return it directly
        if (text.length() <= MAX_LINE_LENGTH) {
            lines.add(text);
            return lines;
        }
        
        // Split text into words
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            // Check if adding this word would exceed the limit
            if (currentLine.length() + word.length() + 1 > MAX_LINE_LENGTH) {
                // Add current line and start a new one
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                // Add word to current line
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }
        
        // Add the last line if not empty
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        
        return lines;
    }
    
    /**
     * Converts text to GLSL character representation
     * @param text Text to convert
     * @return Array of GLSL character identifiers
     */
    private static String[] convertTextToGLSLChars(String text) {
        List<String> result = new ArrayList<>();
        
        for (char c : text.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                // Handle basic Latin letters only (a-z, A-Z)
                result.add("_" + c);
            } else if (c >= '0' && c <= '9') {
                // Handle basic digits only (0-9)
                result.add("_" + c);
            } else if (CHAR_TO_GLSL.containsKey(c)) {
                // Handle special characters from our defined map
                result.add(CHAR_TO_GLSL.get(c));
            }
            // Skip unsupported characters
        }
        
        return result.toArray(new String[0]);
    }
}