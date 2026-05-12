package com.euphoriapatches.euphoria_patcher.services;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector.ShaderInfo;
import com.euphoriapatches.euphoria_patcher.io.ArchiveOperations;
import com.euphoriapatches.euphoria_patcher.io.ArchiveUtils;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.io.FileUtils;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.ui.FileUI;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Handles shader patching operations
 */
public class ShaderPatchingService {
    private final String patchName;
    private final String patchVersion;
    private final String commonLocation;
    private final Path shaderpacks;
    @SuppressWarnings("unused")
    private final ShaderNamingService namingService;

    public ShaderPatchingService(String patchName, String patchVersion, String commonLocation,
                                Path shaderpacks, ShaderNamingService namingService) {
        this.patchName = patchName;
        this.patchVersion = patchVersion;
        this.commonLocation = commonLocation;
        this.shaderpacks = shaderpacks;
        this.namingService = namingService;
    }

    /**
     * Process and patch shaders
     */
    public boolean processAndPatchShaders(ShaderInfo info, Path temp) {
        try {
            if (info.baseFile == null) {
                return false;
            }

            // Get base name and remove .zip extension
            String baseName = info.baseFile.getFileName().toString().replace(".zip", "");
            baseName = ShaderNamingService.cleanBaseName(baseName);

            String patchedName = baseName + " + " + patchName + patchVersion;

            Path baseExtracted = temp.resolve(baseName);
            baseExtracted = ArchiveOperations.extract(info.baseFile, baseExtracted, "extracting archive");
            if (baseExtracted == null) return false;

            normalizeShaderStyleInCommon(baseExtracted);

            Path baseArchived = temp.resolve(baseName + ".tar");
            baseArchived = ArchiveOperations.archive(baseExtracted, baseArchived);

            if (baseArchived != null && !ArchiveOperations.verifyBaseArchive(baseArchived, info.baseFile.getFileName().toString())) return false;

            return applyPatch(baseArchived, temp, patchedName, info.styleUnbound, info.styleReimagined);
        } finally {
            ArchiveOperations.deleteTempDirectory(temp);
        }
    }



    /**
     * Update common file to normalize shader style
     */
    public void normalizeShaderStyleInCommon(Path baseExtracted) {
        try {
            Path commons = baseExtracted.resolve(commonLocation);
            String config = FileUtils.readFileToString(commons.toFile(), "UTF-8").replaceFirst("SHADER_STYLE [14]", "SHADER_STYLE 1");
            FileUtils.writeStringToFile(commons.toFile(), config, "UTF-8");
        } catch (IOException e) {
            log(3, "Error normalizing shader style in common file: " + e.getMessage());
        }
    }

    /**
     * Apply patch
     */
    public boolean applyPatch(Path baseArchived, Path temp, String patchedName, boolean styleUnbound, boolean styleReimagined) {
        Path patchedArchive = temp.resolve(patchedName + ".tar");
        Path patchedFile = shaderpacks.resolve(patchedName);

        return applyProductionPatch(baseArchived, patchedArchive, temp.resolve(patchedName + ".patch"),
                patchedFile, styleUnbound, styleReimagined);
    }

    /**
     * Apply production patch
     */
    public boolean applyProductionPatch(Path baseArchived, Path patchedArchive, Path patchFile, Path patchedFile, boolean styleUnbound, boolean styleReimagined) {
        String patchResourceName = patchName + patchVersion + ".patch";
        debugLog("Attempting to load patch resource: " + patchResourceName);

        try (InputStream patchStream = getClass().getClassLoader().getResourceAsStream(patchResourceName)) {
            if (patchStream != null) {
                debugLog("Patch resource found, copying to: " + patchFile);
                FileUtils.copyInputStreamToFile(Objects.requireNonNull(patchStream), patchFile.toFile());

                debugLog("Applying patch from " + baseArchived + " to " + patchedArchive);
                FileUI.patch(baseArchived.toFile(), patchedArchive.toFile(), patchFile.toFile());

                debugLog("Extracting patched archive to: " + patchedFile);
                try {
                    ArchiveUtils.extract(patchedArchive, patchedFile);
                } catch (IOException | ArchiveException e) {
                    log(2, "Error extracting archive: " + e.getMessage());
                    debugLog("Error extracting archive: " + e.getMessage());
                    return false;
                }

                debugLog("Applying style settings...");
                applyStyleSettings(patchedFile, styleUnbound, styleReimagined);
                log(1, patchName + " was successfully installed. Enjoy! -SpacEagle17");
                return true;
            } else {
                // Patch resource not found - this is a critical error
                debugLog("CRITICAL ERROR: Patch resource not found: " + patchResourceName);
                log(3, 12, "========================");
                log(3, 12, "CRITICAL BUILD ERROR");
                log(3, 12, "========================");
                log(3, 12, "Patch file not found in mod resources!");
                log(3, 12, "Expected: " + patchResourceName);
                log(3, 12, "");
                log(3, 12, "This indicates the mod was built incorrectly.");
                log(3, 12, "The patch file should be in the JAR resources.");
                log(3, 12, "");
                log(3, 12, "Please report this issue to the mod developer");
                log(3, 12, "at: https://github.com/EuphoriaPatches/EuphoriaPatcher/issues");
                log(3, 12, "========================");
            }
        } catch (IOException | CompressorException | InvalidHeaderException e) {
            debugLog("Error applying patch file: " + e.getMessage());
            log(3, "Error applying patch file: " + e.getMessage());
            log(3, EuphoriaLogger.getStackTrace(e));
        }
        return false;
    }

    /**
     * Apply style settings
     */
    public void applyStyleSettings(Path patchedFile, boolean styleUnbound, boolean styleReimagined) throws IOException {
        if (!styleUnbound && !styleReimagined) return;

        File commons = new File(patchedFile.toFile(), commonLocation);
        String commonContent = FileUtils.readFileToString(commons, "UTF-8");

        // Create both style configs
        String reimaginedConfig = commonContent.replaceFirst("SHADER_STYLE [14]", "SHADER_STYLE 1");
        String unboundConfig = commonContent.replaceFirst("SHADER_STYLE [14]", "SHADER_STYLE 4");

        if (!styleReimagined) {
            // Only Unbound style
            FileUtils.writeStringToFile(commons, unboundConfig, "UTF-8");
            return;
        }

        if (!styleUnbound) {
            // Only Reimagined style
            FileUtils.writeStringToFile(commons, reimaginedConfig, "UTF-8");
            return;
        }

        // Handle both styles
        boolean isReimagined = patchedFile.getFileName().toString().contains("Reimagined");
        String otherStyle = isReimagined ? "Unbound" : "Reimagined";
        String currentStyle = isReimagined ? "Reimagined" : "Unbound";

        File otherStyleFile = new File(patchedFile.getParent().toFile(),
                patchedFile.getFileName().toString().replace(currentStyle, otherStyle));

        // Only create the other style if it doesn't already exist
        // This prevents overwriting an existing installation when patching the missing style
        if (!otherStyleFile.exists()) {
            debugLog("Creating missing " + otherStyle + " style from newly patched " + currentStyle);
            FileUtils.copyDirectory(patchedFile.toFile(), otherStyleFile);

            // Apply correct config to the newly created other style
            if (isReimagined) {
                // Current is Reimagined, created Unbound
                FileUtils.writeStringToFile(new File(otherStyleFile, commonLocation), unboundConfig, "UTF-8");
            } else {
                // Current is Unbound, created Reimagined
                FileUtils.writeStringToFile(new File(otherStyleFile, commonLocation), reimaginedConfig, "UTF-8");
            }
        } else {
            debugLog("Other style (" + otherStyle + ") already exists, not overwriting");
        }

        // Apply correct config to the current style
        if (isReimagined) {
            FileUtils.writeStringToFile(commons, reimaginedConfig, "UTF-8");
        } else {
            FileUtils.writeStringToFile(commons, unboundConfig, "UTF-8");
        }
    }

    private void log(int level, String message) {
        EuphoriaPatcher.log(level, message);
    }

    @SuppressWarnings("SameParameterValue")
    private void log(int level, int fadeTimer, String message) {
        EuphoriaPatcher.log(level, fadeTimer, message);
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderPatchingService] " + message);
    }
}
