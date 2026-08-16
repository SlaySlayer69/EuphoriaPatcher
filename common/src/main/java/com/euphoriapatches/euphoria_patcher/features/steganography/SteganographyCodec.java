package com.euphoriapatches.euphoria_patcher.features.steganography;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Pure LSB steganography codec for embedding raw Deflate-compressed UTF-8 text into flat channel
 * byte arrays. No file I/O, LWJGL, or Minecraft dependencies - just {@code byte[]} in, {@code byte[]}/
 * {@link String} out. See {@link ShaderSteganography} for the PNG I/O and reflection-based
 * embedding that sits on top of this.
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
public class SteganographyCodec {
    private static final byte[] MAGIC = {'E', 'U', 'P', 'H'};
    private static final int HEADER_SIZE_BYTES = 12; // 4 magic + 4 CRC32 + 4 length
    private static final int HEADER_SIZE_BITS = HEADER_SIZE_BYTES * 8;
    private static final int EUPHORIA_FLAG_BIT_INDEX = HEADER_SIZE_BITS;
    private static final int TEXT_START_BIT = HEADER_SIZE_BITS + 1;

    // Usable channels per pixel for capacity estimation - alpha is deliberately left untouched.
    public static final int BITS_PER_PIXEL = 3;

    private SteganographyCodec() {
    }

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[SteganographyCodec] " + message);
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
                + " compressed bytes (" + header.length + " byte header + 1 EP flag bit + compressed text with " + String.format("%.1f", (1.0 - (double) compressedBytes.length / rawTextBytes.length) * 100.0) + "% space saved), "
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
