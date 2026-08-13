package com.euphoriapatches.euphoria_patcher.features.steganography;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageWrite;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Decoupled LSB steganography for embedding raw Deflate-compressed UTF-8 text into flat channel byte arrays.
 * <p>
 * Payload layout (big-endian fields; CRC covers compressed payload only):
 * <ul>
 *   <li><b>Bits 0..31:</b> "EUPH" magic (4 bytes ASCII)</li>
 *   <li><b>Bits 32..63:</b> CRC32 checksum (4 bytes)</li>
 *   <li><b>Bits 64..95:</b> Compressed byte length (4 bytes)</li>
 *   <li><b>Bit 96:</b> Euphoria Patches shader flag (1 bit)</li>
 *   <li><b>Bit 97+:</b> Deflate-compressed UTF-8 payload</li>
 * </ul>
 */
public class ShaderSteganography {
    private static final byte[] MAGIC = {'E', 'U', 'P', 'H'};
    private static final int HEADER_SIZE_BYTES = 12; // 4 magic + 4 CRC32 + 4 length
    private static final int HEADER_SIZE_BITS = HEADER_SIZE_BYTES * 8;
    private static final int EUPHORIA_FLAG_BIT_INDEX = HEADER_SIZE_BITS;
    private static final int TEXT_START_BIT = HEADER_SIZE_BITS + 1;

    // Usable channels per pixel for capacity estimation - alpha is deliberately left untouched.
    public static final int BITS_PER_PIXEL = 3;

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
     * Returns true if the given text payload will fit in an image of the given dimensions,
     * false otherwise. Does not actually embed anything.
     */
    public static boolean hasCapacity(int width, int height, String text) {
        if (text == null || text.isEmpty()) {
            debugLog("hasCapacity() called with empty text");
            return false;
        }
        if (width <= 0 || height <= 0) {
            debugLog("hasCapacity() called with non-positive dimensions: " + width + "x" + height);
            return false;
        }

        long capacityBits = (long) width * height * BITS_PER_PIXEL;
        long neededBits = requiredChannelBits(text);
        boolean fits = neededBits <= capacityBits;

        if (!fits) {
            debugLog("Capacity check failed: need " + neededBits + " bits (" + (neededBits / 8) + " bytes incl. header), "
                    + "image provides " + capacityBits + " bits (" + width + "x" + height + " @ " + BITS_PER_PIXEL + " bits/pixel)");
        } else {
            debugLog("Capacity check passed: need " + neededBits + " bits, image provides " + capacityBits + " bits");
        }

        return fits;
    }

    /**
     * Calculates the total channel bytes (1 bit per byte) required to embed {@code text},
     * including header overhead. Compresses the UTF-8 text to determine exact payload size.
     */
    public static long requiredChannelBits(String text) {
        if (text == null) return 0;
        byte[] compressedBytes = compress(text.getBytes(StandardCharsets.UTF_8));
        return (long) TEXT_START_BIT + ((long) compressedBytes.length * 8);
    }

    /**
     * Embeds {@code text} into the LSB of every byte in {@code channelData}, in place.
     *
     * @param channelData      flat array of channel bytes (e.g. R,G,B,R,G,B,...), modified in
     *                         place on success and left untouched on failure
     * @param text             the text payload to embed
     * @param isEuphoriaShader whether the currently loaded shaderpack is Euphoria Patches
     * @return true if the payload fit and was embedded, false otherwise
     */
    public static boolean encode(byte[] channelData, String text, boolean isEuphoriaShader) {
        if (channelData == null) {
            debugLog("encode() called with null channel data, aborting");
            return false;
        }
        if (text == null || text.isEmpty()) {
            debugLog("encode() called with empty text, nothing to embed");
            return false;
        }

        byte[] rawTextBytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] compressedBytes = compress(rawTextBytes);
        byte[] header = buildHeader(compressedBytes);

        long totalBits = (long) TEXT_START_BIT + ((long) compressedBytes.length * 8);
        if (totalBits > channelData.length) {
            debugLog("Payload too large for available capacity: need " + totalBits + " bits ("
                    + header.length + " byte header + 1 flag bit + " + compressedBytes.length
                    + " compressed bytes), have " + channelData.length + " channel bytes - skipping encode");
            return false;
        }

        int bitIndex = writeBitsFromBytes(channelData, 0, header);

        // Clears LSB using 0xFE (0b11111110) and ORs in the 1-bit shader flag, preserving the upper 7 bits.
        channelData[bitIndex] = (byte) ((channelData[bitIndex] & 0xFE) | (isEuphoriaShader ? 1 : 0));
        bitIndex++;
        writeBitsFromBytes(channelData, bitIndex, compressedBytes);

        debugLog("Encoded payload: " + rawTextBytes.length + " raw text bytes -> " + compressedBytes.length
                + " compressed bytes (" + header.length + " byte header + 1 EP flag bit + compressed text with " + String.format("%.1f", (double) compressedBytes.length / rawTextBytes.length * 100.0) + "% compression), "
                + "isEuphoriaShader=" + isEuphoriaShader + ", used " + totalBits + "/" + channelData.length
                + " available channel bits (" + String.format("%.1f", totalBits * 100.0 / channelData.length) + "%)");
        return true;
    }

    /**
     * Writes bytes into {@code channelData} starting at {@code startBit} (MSB to LSB, 1 bit per channel byte).
     * @return Next free bit index for chained sequential writes.
     */
    private static int writeBitsFromBytes(byte[] channelData, int startBit, byte[] sourceBytes) {
        int bitIndex = startBit;
        for (byte sourceByte : sourceBytes) {
            for (int bit = 7; bit >= 0; bit--) {
                int value = (sourceByte >> bit) & 1; // isolate that single bit (0 or 1)
                channelData[bitIndex] = (byte) ((channelData[bitIndex] & 0xFE) | value);
                bitIndex++;
            }
        }
        return bitIndex;
    }

    /**
     * Extracts a previously embedded text payload from {@code channelData}. Fails gracefully
     * (returns null, never throws) for images that were never tagged, or whose tagged data is
     * missing, truncated, or corrupted.
     *
     * @param channelData flat array of channel bytes (e.g. R,G,B,R,G,B,...)
     * @return the embedded text, or null if no valid payload was found
     */
    public static String decode(byte[] channelData) {
        if (channelData == null) {
            debugLog("decode() called with null channel data, aborting");
            return null;
        }

        if (channelData.length < TEXT_START_BIT) {
            debugLog("Not enough channel data for a header: have " + channelData.length + " bits, need " + TEXT_START_BIT);
            return null;
        }

        try {
            byte[] header = extractBytes(channelData, 0, HEADER_SIZE_BYTES);

            for (int i = 0; i < MAGIC.length; i++) {
                if (header[i] != MAGIC[i]) {
                    debugLog("Magic header mismatch at byte " + i + " - not a steganography-tagged image");
                    return null;
                }
            }

            int expectedCrc = readInt(header, 4);
            int length = readInt(header, 8);
            boolean isEuphoriaShader = (channelData[EUPHORIA_FLAG_BIT_INDEX] & 1) != 0;

            if (length <= 0) {
                debugLog("Decoded payload length is non-positive (" + length + "), rejecting");
                return null;
            }

            long totalBits = (long) TEXT_START_BIT + ((long) length * 8);
            if (totalBits > channelData.length) {
                debugLog("Decoded payload length (" + length + " bytes) exceeds available channel data ("
                        + channelData.length + " bits available, " + totalBits + " needed) - truncated or corrupt, rejecting");
                return null;
            }

            byte[] compressedBytes = extractBytes(channelData, TEXT_START_BIT, length);

            CRC32 crc32 = new CRC32();
            crc32.update(compressedBytes, 0, compressedBytes.length);
            int actualCrc = (int) crc32.getValue();

            String magicText = new String(header, 0, MAGIC.length, StandardCharsets.US_ASCII);
            debugLog("Header: First bits are: " + magicText + " - CRC32 Checksum: expected: " + Integer.toHexString(expectedCrc)
                    + ", image: " + Integer.toHexString(actualCrc)
                    + " - Is Euphoria Patches Settings: " + isEuphoriaShader
                    + " - Compressed Data Size: " + length + " Bytes (" + String.format("%.1f", length / 1024.0) + " kB)"
                    + " - Total Bits: " + totalBits + "/" + channelData.length + " (" + String.format("%.1f", totalBits * 100.0 / channelData.length) + "%)");

            if (actualCrc != expectedCrc) {
                debugLog("CRC32 checksum mismatch - corrupt payload, rejecting");
                return null;
            }

            byte[] rawTextBytes;
            try {
                rawTextBytes = decompress(compressedBytes);
            } catch (DataFormatException e) {
                debugLog("Failed to decompress payload - corrupt data, rejecting: " + e.getMessage());
                return null;
            }

            String text = new String(rawTextBytes, StandardCharsets.UTF_8);
            debugLog("Decoded payload successfully: " + length + " compressed bytes -> " + rawTextBytes.length + " text bytes");
            return text;
        } catch (Exception e) {
            debugLog("Unexpected error while decoding, treating as no payload: " + e);
            return null;
        }
    }

    /**
     * Reads a PNG file's pixels via LWJGL's STBImage (not java.awt/ImageIO because MC disables AWT headless mode)
     * and extracts any embedded text payload via {@link #decode(byte[])}.
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
            byte[] channelData = new byte[width * height * BITS_PER_PIXEL];
            for (int i = 0; i < channelData.length; i++) {
                channelData[i] = pixels.get(i);
            }

            String text = decode(channelData);
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

        long neededBitsLong = requiredChannelBits(text);
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
            byte[] channelData = new byte[width * height * BITS_PER_PIXEL];
            for (int i = 0; i < channelData.length; i++) {
                channelData[i] = pixels.get(i);
            }

            if (neededBits > channelData.length) {
                debugLog("Needed bits (" + neededBits + ") exceed decoded channel data (" + channelData.length
                        + ") for " + pngFile.getFileName() + " - skipping debug visualization");
                return;
            }

            // Preserves upper nibble (0xF0) and expands the LSB (bit 0) across all 4 low bits (0x0F/0x00),
            // amplifying a 1-bit embedded value into a human-visible channel swing for debugging.
            for (int i = 0; i < neededBits; i++) {
                int bitValue = channelData[i] & 1;
                int lowNibble = bitValue == 1 ? 0x0F : 0x00;
                channelData[i] = (byte) ((channelData[i] & 0xF0) | lowNibble);
            }

            Path debugFile = debugFilePath(pngFile);
            ByteBuffer outputBuffer = ByteBuffer.allocateDirect(channelData.length);
            outputBuffer.put(channelData).flip();

            boolean success = STBImageWrite.stbi_write_png(debugFile.toAbsolutePath().toString(), width, height,
                    BITS_PER_PIXEL, outputBuffer, width * BITS_PER_PIXEL);
            if (!success) {
                debugLog("STBImageWrite failed to write debug visualization to " + debugFile);
            } else {
                debugLog("Wrote debug visualization: " + debugFile.getFileName() + " (" + neededBits + " channel bytes amplified)");
            }

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
        String name = pngFile.getFileName().toString();
        String baseName = name.toLowerCase(Locale.ROOT).endsWith(".png") ? name.substring(0, name.length() - 4) : name;
        Path parent = pngFile.getParent();
        return parent != null ? parent.resolve(baseName + "-debug.png") : Paths.get(baseName + "-debug.png");
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
     * Performs two passes: reads and caches packed colors while extracting RGB channel bytes for {@link #encode},
     * then merges modified channel LSBs back into cached colors and invokes {@code pixelSetter}.
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
        if (!hasCapacity(width, height, text)) {
            return false; // hasCapacity() already logged why
        }

        long neededBitsLong = requiredChannelBits(text);
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

                int channelsThisPixel = Math.min(BITS_PER_PIXEL, neededBits - filled);
                for (int c = 0; c < channelsThisPixel; c++) {
                    // Reads low 3 color bytes of the packed int, skipping byte 3 (alpha).
                    channelData[filled++] = (byte) (packed >>> (c * 8));
                }
            }

            if (!encode(channelData, text, isEuphoriaShader)) {
                return false; // encode() already logged why
            }

            filled = 0;
            for (int pixelIndex = 0; pixelIndex < pixelsNeeded; pixelIndex++) {
                int x = pixelIndex % width;
                int y = pixelIndex / width;
                int packed = originalPixels[pixelIndex];

                int channelsThisPixel = Math.min(BITS_PER_PIXEL, neededBits - filled);
                for (int c = 0; c < channelsThisPixel; c++) {
                    int shift = c * 8; // Bit position for this channel byte: Red=0, Green=8, Blue=16
                    int newByteValue = channelData[filled++] & 0xFF;
                    // 1. (0xFF << shift) creates a byte mask over channel 'c'
                    // 2. ~ zeroes out channel 'c' while keeping the other 3 bytes intact
                    // 3. | splices the updated channel byte into the zeroed slot
                    packed = (packed & ~(0xFF << shift)) | (newByteValue << shift);
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

    private static byte[] buildHeader(byte[] compressedBytes) {
        CRC32 crc32 = new CRC32();
        crc32.update(compressedBytes, 0, compressedBytes.length);
        int crc = (int) crc32.getValue();

        byte[] header = new byte[HEADER_SIZE_BYTES];
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
        writeInt(header, 4, crc);
        writeInt(header, 8, compressedBytes.length);
        return header;
    }

    // Raw Deflate (nowrap=true, no zlib header/Adler32 trailer) since we already have our own
    // CRC32 in the payload header - the zlib wrapper would just be a few redundant bytes.
    private static byte[] compress(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(input);
            deflater.finish();

            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(32, input.length / 2));
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    // Inverse of compress() - must use the same nowrap=true raw Deflate mode.
    private static byte[] decompress(byte[] input) throws DataFormatException {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(input);

            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(32, input.length * 2));
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    // Guards against an infinite loop on truncated/corrupt input: finished()
                    // stays false but inflate() can no longer make progress either way.
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw new DataFormatException("Truncated or corrupt compressed data");
                    }
                    break;
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            inflater.end();
        }
    }

    // Reassembles byteCount bytes by reading the LSB from sequential channel bytes in MSB-first order.
    private static byte[] extractBytes(byte[] channelData, int startBit, int byteCount) {
        byte[] result = new byte[byteCount];
        for (int i = 0; i < byteCount; i++) {
            int value = 0;
            for (int bit = 0; bit < 8; bit++) { // 8 = bits per byte
                // Shift what we've accumulated so far left to make room, then OR in the next
                // bit - "& 1" reads only the channel byte's LSB, ignoring the other 7 bits
                // (the actual pixel color data, which we deliberately never touch here).
                value = (value << 1) | (channelData[startBit + i * 8 + bit] & 1);
            }
            result[i] = (byte) value;
        }
        return result;
    }

    // Packs a 32-bit integer into 4 bytes in big-endian byte order (most significant byte first).
    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    // Reassembles 4 big-endian bytes into a 32-bit integer (& 0xFF converts signed bytes to unsigned to prevent bitwise corruption).
    private static int readInt(byte[] source, int offset) {
        return ((source[offset] & 0xFF) << 24)
                | ((source[offset + 1] & 0xFF) << 16)
                | ((source[offset + 2] & 0xFF) << 8)
                | (source[offset + 3] & 0xFF);
    }
}
