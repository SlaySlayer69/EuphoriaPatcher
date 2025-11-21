package mc.euphoria_patches.euphoria_patcher;

import io.sigpipe.jbsdiff.ui.FileUI;
import mc.euphoria_patches.euphoria_patcher.util.ArchiveUtils;
import mc.euphoria_patches.euphoria_patcher.util.ModLoaderSpecifics;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Standalone patch generator for development purposes.
 * This can be run directly from IntelliJ to generate .patch files without needing to launch Minecraft.
 * 
 * Usage:
 * 1. Ensure you have the base shader and patched shader in your shaderpacks folder
 * 2. Run this class directly (it has a main method)
 * 3. The .patch files will be generated in common/src/main/resources and EuphoriaPatchFiles
 */
public class DevPatchGenerator {
    
    /**
     * Flag to indicate if we're in development mode.
     * This is used by ArchiveOperations to skip verification during patch generation.
     */
    public static boolean IS_DEV_MODE = false;
    
    // ANSI escape codes for colored output
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String YELLOW = "\u001B[33m";
    
    // Constants - use PatchInfo directly to avoid EuphoriaPatcher initialization
    private static final String BRAND_NAME = "Complementary";
    private static final String PATCH_NAME = "EuphoriaPatches";
    private static final String VERSION = PatchInfo.VERSION;
    private static final String PATCH_VERSION = PatchInfo.PATCH_VERSION;
    
    // Get the IntelliJ project base directory
    private static final Path INTELLIJ_BASE_DIR = Paths.get("").toAbsolutePath();
    
    // Derive paths from the base directory
    private static final Path RESOURCES_DIR = INTELLIJ_BASE_DIR.resolve("common/src/main/resources");
    private static final Path PATCH_FILES_DIR = INTELLIJ_BASE_DIR.resolve("EuphoriaPatchFiles");
    private static final Path SHADERPACKS_DIR = INTELLIJ_BASE_DIR.resolve("shaderpacks");
    
    public static void main(String[] args) {
        System.out.println(BLUE + "=== Euphoria Patches - Dev Patch Generator ===" + RESET);
        System.out.println("IntelliJ Base Directory: " + BLUE + INTELLIJ_BASE_DIR + RESET);
        System.out.println();
        
        // Set dev mode flag for ArchiveOperations
        IS_DEV_MODE = true;
        
        // Initialize a dummy ModLoaderSpecifics to avoid initialization errors
        initializeDummyModLoaderSpecifics();
        
        // Ensure shaderpacks folder exists
        try {
            ensureDirectoryExists(SHADERPACKS_DIR);
        } catch (IOException e) {
            System.err.println(RED + "ERROR: Could not create shaderpacks folder!" + RESET);
            System.exit(1);
        }
        
        System.out.println("Shaderpacks folder: " + BLUE + SHADERPACKS_DIR + RESET);
        System.out.println();
        
        try {
            // Find base and patched shaders
            ShaderPair shaderPair = findShaders();
            if (shaderPair == null) {
                System.err.println(RED + "ERROR: Could not find both base and patched shaders!" + RESET);
                System.exit(1);
            }
            
            System.out.println(GREEN + "Found base shader: " + BLUE + shaderPair.baseShader.getFileName() + RESET);
            System.out.println(GREEN + "Found patched shader: " + BLUE + shaderPair.patchedShader.getFileName() + RESET);
            System.out.println();
            
            // Generate patch files
            generatePatchFiles(shaderPair);
            
            System.out.println();
            System.out.println(GREEN + "=== Patch generation completed successfully! ===" + RESET);
            
        } catch (Exception e) {
            System.err.println(RED + "ERROR: " + e.getMessage() + RESET);
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Initialize a dummy ModLoaderSpecifics implementation to avoid initialization errors
     */
    private static void initializeDummyModLoaderSpecifics() {
        ModLoaderSpecifics.setInstance(new ModLoaderSpecifics() {
            @Override
            public Path getShaderpacksPath() {
                return SHADERPACKS_DIR;
            }

            @Override
            public Path getConfigDirectory() {
                return INTELLIJ_BASE_DIR.resolve("config");
            }

            @Override
            public boolean isDevMode() {
                return true;
            }

            @Override
            public boolean serverCheck() {
                return false;
            }

            @Override
            public String getCurrentDimension() {
                return "overworld";
            }
        });
    }
    
    /**
     * Finds the base and patched shader files
     */
    private static ShaderPair findShaders() throws IOException {
        Path baseShader = null;
        Path patchedShader = null;
        
        // Look for base shader (e.g., ComplementaryReimagined_r5.3.zip)
        // and patched shader (e.g., ComplementaryReimagined_r5.3 + EuphoriaPatches_1.7.8)
        for (Path path : Files.newDirectoryStream(SHADERPACKS_DIR)) {
            String name = path.getFileName().toString();
            
            // Check if it's a base shader
            if (name.startsWith(BRAND_NAME) && 
                name.contains(VERSION) &&
                !name.contains(PATCH_NAME)) {
                baseShader = path;
            }
            
            // Check if it's the patched shader
            if (name.contains(BRAND_NAME) &&
                name.contains(" + " + PATCH_NAME + PATCH_VERSION)) {
                patchedShader = path;
            }
            
            if (baseShader != null && patchedShader != null) {
                break;
            }
        }
        
        if (baseShader == null || patchedShader == null) {
            return null;
        }
        
        return new ShaderPair(baseShader, patchedShader);
    }
    
    /**
     * Generates the .patch files
     */
    private static void generatePatchFiles(ShaderPair shaderPair) throws Exception {
        System.out.println(YELLOW + "Creating temporary directory..." + RESET);
        Path tempDir = Files.createTempDirectory("euphoria-patch-gen-");
        
        try {
            // Extract base shader (if it's a zip) or copy directory
            System.out.println(YELLOW + "Extracting base shader..." + RESET);
            Path baseExtracted = tempDir.resolve("base-extracted");
            extractOrCopyShader(shaderPair.baseShader, baseExtracted);
            
            // Extract patched shader (if it's a zip) or copy directory
            System.out.println(YELLOW + "Extracting patched shader..." + RESET);
            Path patchedExtracted = tempDir.resolve("patched-extracted");
            extractOrCopyShader(shaderPair.patchedShader, patchedExtracted);
            
            // Archive base shader to TAR
            System.out.println(YELLOW + "Creating TAR archive of base shader..." + RESET);
            Path baseArchived = tempDir.resolve("base.tar");
            ArchiveUtils.archive(baseExtracted, baseArchived);
            
            // Archive patched shader to TAR
            System.out.println(YELLOW + "Creating TAR archive of patched shader..." + RESET);
            Path patchedArchived = tempDir.resolve("patched.tar");
            ArchiveUtils.archive(patchedExtracted, patchedArchived);
            
            // Ensure output directories exist
            ensureDirectoryExists(RESOURCES_DIR);
            ensureDirectoryExists(PATCH_FILES_DIR);
            
            // Generate patch files
            String patchFileName = PATCH_NAME + PATCH_VERSION + ".patch";
            
            System.out.println(YELLOW + "Generating patch file: " + patchFileName + RESET);
            System.out.println(YELLOW + "This may take a moment..." + RESET);
            
            // Generate to resources directory
            Path resourcesPatchFile = RESOURCES_DIR.resolve(patchFileName);
            FileUI.diff(baseArchived.toFile(), patchedArchived.toFile(), resourcesPatchFile.toFile());
            System.out.println(GREEN + "✓ Created: " + BLUE + resourcesPatchFile + RESET);
            
            // Generate to EuphoriaPatchFiles directory
            Path patchFilesDirPatch = PATCH_FILES_DIR.resolve(patchFileName);
            FileUI.diff(baseArchived.toFile(), patchedArchived.toFile(), patchFilesDirPatch.toFile());
            System.out.println(GREEN + "✓ Created: " + BLUE + patchFilesDirPatch + RESET);
            
        } finally {
            // Clean up temp directory
            System.out.println(YELLOW + "Cleaning up temporary files..." + RESET);
            try {
                FileUtils.deleteDirectory(tempDir.toFile());
            } catch (IOException e) {
                System.err.println(YELLOW + "Warning: Could not delete temp directory: " + e.getMessage() + RESET);
            }
        }
    }
    
    /**
     * Extracts a shader pack (if it's a zip) or copies it (if it's a directory)
     */
    private static void extractOrCopyShader(Path shaderPath, Path outputDir) throws Exception {
        if (Files.isDirectory(shaderPath)) {
            // It's already a directory, just copy it
            System.out.println("  " + BLUE + shaderPath.getFileName() + RESET + " is a directory, copying...");
            FileUtils.copyDirectory(shaderPath.toFile(), outputDir.toFile());
        } else {
            // It's a file (probably a zip), extract it
            System.out.println("  " + BLUE + shaderPath.getFileName() + RESET + " is an archive, extracting...");
            ArchiveUtils.extract(shaderPath, outputDir);
        }
    }
    
    /**
     * Ensures a directory exists, creating it if necessary
     */
    private static void ensureDirectoryExists(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            System.out.println(YELLOW + "Creating directory: " + BLUE + dir + RESET);
            Files.createDirectories(dir);
        }
    }
    
    /**
     * Helper class to hold base and patched shader paths
     */
    private static class ShaderPair {
        final Path baseShader;
        final Path patchedShader;
        
        ShaderPair(Path baseShader, Path patchedShader) {
            this.baseShader = baseShader;
            this.patchedShader = patchedShader;
        }
    }
}
