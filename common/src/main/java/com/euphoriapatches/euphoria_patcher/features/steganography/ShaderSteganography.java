package com.euphoriapatches.euphoria_patcher.features.steganography;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageWrite;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * PNG file I/O and reflection-based pixel embedding for LSB steganography. Reads/writes
 * screenshot pixels via LWJGL's STBImage (not java.awt/ImageIO, since Minecraft disables AWT
 * headless mode) and via reflected {@code NativeImage} accessors, delegating the actual bit
 * packing to {@link SteganographyCodec}.
 */
public class ShaderSteganography {
    // Usable channels per pixel for capacity estimation, blue only (lowest luminance
    // contribution), red/green/alpha deliberately left untouched.
    public static final int BITS_PER_PIXEL = SteganographyCodec.BITS_PER_PIXEL;

    // Byte offset of blue within an STBImage-decoded RGB triplet, and bit-shift of blue within a
    // packed ABGR/RGBA color int (Red=0, Green=8, Blue=16)
    private static final int BLUE_BYTE_OFFSET = 2;
    private static final int BLUE_BIT_SHIFT = 16;

    private ShaderSteganography() {
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderSteganography] " + message);
    }

    /**
     * Reads the current shaderpack's .txt settings file (if any) and returns it as a UTF-8
     * string, or null if no shaderpack is loaded or no settings file exists for it.
     */
    public static String readEmbeddableText() {
        String shaderpackName = ShaderLoader.getCurrentShaderpackName();
        if (shaderpackName == null) {
            debugLog("No shaderpack currently loaded, nothing to embed");
            return null;
        }

        Path settingsTextPath = EuphoriaPatcher.shaderpacks.resolve(shaderpackName + ".txt");
        if (!Files.exists(settingsTextPath)) {
            debugLog("No shader settings file for '" + shaderpackName + "' (looked for " + settingsTextPath.getFileName() + "), nothing to embed");
            return null;
        }

        try {
            byte[] bytes = Files.readAllBytes(settingsTextPath);
            String text = new String(bytes, StandardCharsets.UTF_8);
            debugLog("Read " + bytes.length + " byte shader settings file for '" + shaderpackName + "'");
            return text;
        } catch (IOException e) {
            debugLog("Failed to read shader settings file " + settingsTextPath + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Reads a PNG file's pixels via LWJGL's STBImage (not java.awt/ImageIO because MC disables AWT headless mode)
     * and extracts any embedded text payload via {@link SteganographyCodec#decode(byte[])}.
     *
     * @param pngFile path to a PNG file, e.g. a saved screenshot
     * @return the embedded text, or null if the file couldn't be read/decoded, or had no valid payload
     */
    public static String decodeFromPngFile(Path pngFile) {
        if (pngFile == null || !Files.exists(pngFile)) {
            debugLog("decodeFromPngFile() called with a missing/null file: " + pngFile);
            return null;
        }

        ByteBuffer pixels = null;
        try {
            byte[] fileBytes = Files.readAllBytes(pngFile);

            // STBImage requires a direct native buffer (not heap-backed byte[] or ByteBuffer.wrap()).
            ByteBuffer fileBuffer = ByteBuffer.allocateDirect(fileBytes.length);
            fileBuffer.put(fileBytes).flip();

            // Must use LWJGL BufferUtils (native-endian) instead of JDK ByteBuffer.allocateDirect()
            // (big-endian default), preventing silent byte-swapping to garbage on x86/x64 little-endian systems.
            IntBuffer widthBuf = BufferUtils.createIntBuffer(1);
            IntBuffer heightBuf = BufferUtils.createIntBuffer(1);
            IntBuffer channelsInFileBuf = BufferUtils.createIntBuffer(1);

            // desired_channels=3 forces STBImage to output normalized sequential RGB bytes, dropping alpha, exactly what we want!
            pixels = STBImage.stbi_load_from_memory(fileBuffer, widthBuf, heightBuf, channelsInFileBuf, 3);
            if (pixels == null) {
                debugLog("STBImage failed to decode " + pngFile + ": " + STBImage.stbi_failure_reason());
                return null;
            }

            int width = widthBuf.get(0);
            int height = heightBuf.get(0);

            // Uses absolute indexed get(i) rather than relative bulk get(channelData). Relative gets advance
            // position to capacity, causing stbi_image_free() to free the wrong pointer and trigger native
            // STATUS_HEAP_CORRUPTION crashes without JVM crash logs. Yes. That was fun :D
            // Stride-3, offset 2: pulls only the blue byte of every pixel out of the interleaved
            // R,G,B,R,G,B,... buffer STBImage returns.
            byte[] channelData = new byte[width * height * BITS_PER_PIXEL];
            for (int i = 0; i < channelData.length; i++) {
                channelData[i] = pixels.get(i * 3 + BLUE_BYTE_OFFSET);
            }

            String text = SteganographyCodec.decode(channelData);
            debugLog("Decoded " + pngFile.getFileName() + " (" + width + "x" + height + "): "
                    + (text != null ? "found payload" : "no payload"));
            return text;
        } catch (IOException e) {
            debugLog("Failed to read " + pngFile + ": " + e.getMessage());
            return null;
        } catch (Throwable t) {
            // STBImage/native-buffer issues (corrupt file, unsupported format, etc.) must
            // never crash the caller - fail gracefully instead.
            debugLog("Unexpected error decoding " + pngFile + ": " + t);
            return null;
        } finally {
            if (pixels != null) {
                STBImage.stbi_image_free(pixels);
            }
        }
    }

    /**
     * DEBUG mode only: saves a visual "-debug.png" copy of a screenshot with embedded LSB bits
     * amplified across channel low-nibbles to make embedding human-visible.
     *
     * @param pngFile Just-saved screenshot file to read and visualize.
     */
    public static void writeDebugVisualization(Path pngFile) {
        String text = readEmbeddableText();
        if (text == null) {
            debugLog("No embeddable text for " + pngFile.getFileName() + ", skipping debug visualization");
            return;
        }

        long neededBitsLong = SteganographyCodec.requiredChannelBits(text);
        if (neededBitsLong > Integer.MAX_VALUE) {
            debugLog("Required capacity absurdly large (" + neededBitsLong + " bits), skipping debug visualization");
            return;
        }
        int neededBits = (int) neededBitsLong;

        ByteBuffer pixels = null;
        try {
            byte[] fileBytes = Files.readAllBytes(pngFile);

            ByteBuffer fileBuffer = ByteBuffer.allocateDirect(fileBytes.length);
            fileBuffer.put(fileBytes).flip();

            IntBuffer widthBuf = BufferUtils.createIntBuffer(1);
            IntBuffer heightBuf = BufferUtils.createIntBuffer(1);
            IntBuffer channelsInFileBuf = BufferUtils.createIntBuffer(1);

            pixels = STBImage.stbi_load_from_memory(fileBuffer, widthBuf, heightBuf, channelsInFileBuf, 3);
            if (pixels == null) {
                debugLog("STBImage failed to decode " + pngFile + " for debug visualization: " + STBImage.stbi_failure_reason());
                return;
            }

            int width = widthBuf.get(0);
            int height = heightBuf.get(0);

            // Uses absolute indexed get(i) rather than relative bulk get(channelData). Relative gets advance
            // position to capacity, causing stbi_image_free() to free the wrong pointer and trigger native
            // STATUS_HEAP_CORRUPTION crashes without JVM crash logs. Yes. That was fun :D
            byte[] fullRgb = new byte[width * height * 3];
            for (int i = 0; i < fullRgb.length; i++) {
                fullRgb[i] = pixels.get(i);
            }
            byte[] channelData = new byte[width * height * BITS_PER_PIXEL];
            for (int i = 0; i < channelData.length; i++) {
                channelData[i] = fullRgb[i * 3 + BLUE_BYTE_OFFSET];
            }

            if (neededBits > channelData.length) {
                debugLog("Needed bits (" + neededBits + ") exceed decoded channel data (" + channelData.length
                        + ") for " + pngFile.getFileName() + " - skipping debug visualization");
                return;
            }

            // Preserves upper nibble (0xF0) and expands the LSB (bit 0) across all 4 low bits (0x0F/0x00),
            // amplifying a 1-bit embedded value into a human-visible channel swing for debugging.
            byte[] amplifiedRgb = fullRgb.clone();
            for (int i = 0; i < neededBits; i++) {
                int bitValue = channelData[i] & 1;
                int lowNibble = bitValue == 1 ? 0x0F : 0x00;
                amplifiedRgb[i * 3 + BLUE_BYTE_OFFSET] = (byte) ((channelData[i] & 0xF0) | lowNibble);
            }

            writeRgbPng(debugFilePath(pngFile), width, height, amplifiedRgb, neededBits + " channel bytes amplified");

            writeDecodedPayloadDump(pngFile);
        } catch (IOException e) {
            debugLog("Failed to read " + pngFile + " for debug visualization: " + e.getMessage());
        } catch (Throwable t) {
            debugLog("Unexpected error writing debug visualization for " + pngFile + ": " + t);
        } finally {
            if (pixels != null) {
                STBImage.stbi_image_free(pixels);
            }
        }
    }

    private static Path debugFilePath(Path pngFile) {
        return suffixedFilePath(pngFile, "-debug.png");
    }

    private static Path withoutDataFilePath(Path pngFile) {
        return suffixedFilePath(pngFile, "-without_data.png");
    }

    private static Path suffixedFilePath(Path pngFile, String suffix) {
        String name = pngFile.getFileName().toString();
        String baseName = name.toLowerCase(Locale.ROOT).endsWith(".png") ? name.substring(0, name.length() - 4) : name;
        Path parent = pngFile.getParent();
        return parent != null ? parent.resolve(baseName + suffix) : Paths.get(baseName + suffix);
    }

    // Writes a plain interleaved RGB byte[] out as a PNG via STBImageWrite, logging success/failure.
    private static void writeRgbPng(Path outputFile, int width, int height, byte[] rgbData, String logDetail) {
        ByteBuffer outputBuffer = ByteBuffer.allocateDirect(rgbData.length);
        outputBuffer.put(rgbData).flip();

        boolean success = STBImageWrite.stbi_write_png(outputFile.toAbsolutePath().toString(), width, height,
                3, outputBuffer, width * 3);
        if (!success) {
            debugLog("STBImageWrite failed to write " + outputFile);
        } else {
            debugLog("Wrote " + outputFile.getFileName() + " (" + logDetail + ")");
        }
    }

    // DEBUG mode only: re-decodes the payload from the saved PNG and writes it to a
    // ".txt" file to verify the full embed -> save -> decode round trip.
    private static void writeDecodedPayloadDump(Path pngFile) {
        String decoded = decodeFromPngFile(pngFile);
        if (decoded == null) {
            debugLog("Could not re-decode " + pngFile.getFileName() + " for the debug payload dump despite an embed having just happened");
            return;
        }

        Path txtFile = txtDumpPath(pngFile);
        try {
            Files.write(txtFile, decoded.getBytes(StandardCharsets.UTF_8));
            debugLog("Wrote decoded payload dump: " + txtFile.getFileName());
        } catch (IOException e) {
            debugLog("Failed to write decoded payload dump " + txtFile + ": " + e.getMessage());
        }
    }

    private static Path txtDumpPath(Path pngFile) {
        String name = pngFile.getFileName().toString() + ".txt";
        Path parent = pngFile.getParent();
        return parent != null ? parent.resolve(name) : Paths.get(name);
    }

    /**
     * Embeds {@code text} into image pixels starting at (0,0), modifying only required pixels.
     * <p>
     * Performs two passes: reads and caches packed colors while extracting RGB channel bytes for
     * {@link SteganographyCodec#encode}, then merges modified channel LSBs back into cached
     * colors and invokes {@code pixelSetter}.
     *
     * @param image            Target image instance.
     * @param width            Image width in pixels.
     * @param height           Image height in pixels.
     * @param pixelGetter      Reflected getter: {@code int get(int x, int y)} returning a packed color.
     * @param pixelSetter      Reflected setter: {@code void set(int x, int y, int color)}.
     * @param text             Text payload to embed.
     * @param isEuphoriaShader Whether current shaderpack is Euphoria Patches.
     * @return {@code true} if payload was embedded; {@code false} if aborted or failed (image untouched).
     */
    public static boolean embedIntoImage(Object image, int width, int height, Method pixelGetter, Method pixelSetter, String text, boolean isEuphoriaShader) {
        if (image == null || pixelGetter == null || pixelSetter == null) {
            debugLog("embedIntoImage() called with a null image/getter/setter, aborting");
            return false;
        }
        if (!SteganographyCodec.hasCapacity(width, height, text)) {
            return false; // hasCapacity() already logged why
        }

        long neededBitsLong = SteganographyCodec.requiredChannelBits(text);
        if (neededBitsLong > Integer.MAX_VALUE - BITS_PER_PIXEL) {
            // Guards (int) cast against integer overflow wrapping.
            debugLog("Required capacity absurdly large (" + neededBitsLong + " bits), aborting");
            return false;
        }
        int neededBits = (int) neededBitsLong;
        int pixelsNeeded = (neededBits + BITS_PER_PIXEL - 1) / BITS_PER_PIXEL; // ceil division

        int[] originalPixels = new int[pixelsNeeded];
        byte[] channelData = new byte[neededBits];

        try {
            int filled = 0;
            for (int pixelIndex = 0; pixelIndex < pixelsNeeded; pixelIndex++) {
                int x = pixelIndex % width;
                int y = pixelIndex / width;
                int packed = (int) pixelGetter.invoke(image, x, y);
                originalPixels[pixelIndex] = packed;

                if (filled < neededBits) {
                    // Blue channel byte only (shift 16): Red=0, Green=8, Blue=16.
                    channelData[filled++] = (byte) (packed >>> BLUE_BIT_SHIFT);
                }
            }

            if (!SteganographyCodec.encode(channelData, text, isEuphoriaShader)) {
                return false; // encode() already logged why
            }

            filled = 0;
            for (int pixelIndex = 0; pixelIndex < pixelsNeeded; pixelIndex++) {
                int x = pixelIndex % width;
                int y = pixelIndex / width;
                int packed = originalPixels[pixelIndex];

                if (filled < neededBits) {
                    int newByteValue = channelData[filled++] & 0xFF;
                    // 1. (0xFF << shift) creates a byte mask over the blue channel
                    // 2. ~ zeroes out blue while keeping the other 3 bytes intact
                    // 3. | splices the updated blue byte into the zeroed slot
                    packed = (packed & ~(0xFF << BLUE_BIT_SHIFT)) | (newByteValue << BLUE_BIT_SHIFT);
                }

                pixelSetter.invoke(image, x, y, packed);
            }

            debugLog("Embedded payload into " + pixelsNeeded + " pixel(s) starting at (0,0)");
            return true;
        } catch (Throwable t) {
            debugLog("Failed to embed payload via reflection: " + t);
            return false;
        }
    }

    /**
     * Reads active shader settings and embeds them into the image via NativeImage mixins.
     * @return {@code true} if settings were embedded, {@code false} if absent or failed.
     */
    public static boolean embedCurrentShaderSettings(Object image, int width, int height, Method pixelGetter, Method pixelSetter) {
        String text = readEmbeddableText();
        if (text == null) {
            return false; // readEmbeddableText() already logged why
        }
        return embedIntoImage(image, width, height, pixelGetter, pixelSetter, text, isCurrentShaderEuphoriaPatches());
    }

    /**
     * [DEBUG ONLY] Saves a pre-embed {@code "-without_data.png"} snapshot via STBImageWrite directly,
     * bypassing file-writing mixin hooks to avoid recursive calls before LSB data is added.
     */
    public static void writeOriginalPixelSnapshot(Object image, int width, int height, Method pixelGetter, Path screenshotFile) {
        if (image == null || pixelGetter == null || width <= 0 || height <= 0) {
            debugLog("writeOriginalPixelSnapshot() called with invalid arguments, skipping");
            return;
        }

        try {
            byte[] rgb = new byte[width * height * 3];
            int i = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int packed = (int) pixelGetter.invoke(image, x, y);
                    rgb[i++] = (byte) packed;
                    rgb[i++] = (byte) (packed >>> 8);
                    rgb[i++] = (byte) (packed >>> BLUE_BIT_SHIFT);
                }
            }

            writeRgbPng(withoutDataFilePath(screenshotFile), width, height, rgb, "true pre-embed pixels, " + width + "x" + height);
        } catch (Throwable t) {
            debugLog("Failed to snapshot original pixels via reflection: " + t);
        }
    }

    /**
     * Whether the currently loaded shaderpack is Euphoria Patches
     */
    private static boolean isCurrentShaderEuphoriaPatches() {
        EuphoriaPatcher patcher = EuphoriaPatcher.getInstance();
        ShaderDetector detector = patcher != null ? patcher.getShaderDetector() : null;
        if (detector == null) {
            debugLog("No ShaderDetector instance available yet, assuming not a Euphoria Patches shader");
            return false;
        }

        Path currentShaderPath = ShaderLoader.getCurrentShaderpackPath();
        boolean isEuphoria = detector.isEuphoriaPatchesShader(currentShaderPath);
        debugLog("Current shaderpack is Euphoria Patches: " + isEuphoria);
        return isEuphoria;
    }
}
