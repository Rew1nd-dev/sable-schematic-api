package dev.rew1nd.sableschematicapi.blueprint.preview;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

final class SableBlueprintPreviewCompression {
    private SableBlueprintPreviewCompression() {
    }

    static byte[] compress(final int[] pixels) {
        final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            final byte[] input = toArgbBytes(pixels);
            deflater.setInput(input);
            deflater.finish();

            final ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(input.length, 64 * 1024));
            final byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                final int written = deflater.deflate(buffer);
                if (written <= 0) {
                    throw new IllegalStateException("Unable to compress Sable blueprint preview.");
                }
                output.write(buffer, 0, written);
            }
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    static int[] decompress(final byte[] data, final int expectedPixels) {
        final int expectedBytes = expectedBytes(expectedPixels);
        final byte[] output = new byte[expectedBytes];
        final Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            int offset = 0;
            while (offset < output.length && !inflater.finished()) {
                final int written = inflater.inflate(output, offset, output.length - offset);
                if (written > 0) {
                    offset += written;
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    break;
                } else {
                    throw new IllegalArgumentException("Unable to decompress Sable blueprint preview.");
                }
            }

            if (offset != output.length || !inflater.finished() || inflater.getRemaining() != 0) {
                throw new IllegalArgumentException("Invalid compressed Sable blueprint preview length.");
            }
            return fromArgbBytes(output, expectedPixels);
        } catch (final DataFormatException e) {
            throw new IllegalArgumentException("Invalid compressed Sable blueprint preview.", e);
        } finally {
            inflater.end();
        }
    }

    static int maxCompressedBytes(final int expectedPixels) {
        final int uncompressedBytes = expectedBytes(expectedPixels);
        return Math.addExact(uncompressedBytes, Math.addExact(uncompressedBytes / 100, 64));
    }

    private static int expectedBytes(final int expectedPixels) {
        return Math.multiplyExact(expectedPixels, Integer.BYTES);
    }

    private static byte[] toArgbBytes(final int[] pixels) {
        final byte[] bytes = new byte[expectedBytes(pixels.length)];
        for (int pixelIndex = 0, byteIndex = 0; pixelIndex < pixels.length; pixelIndex++, byteIndex += Integer.BYTES) {
            final int pixel = pixels[pixelIndex];
            bytes[byteIndex] = (byte) (pixel >>> 24);
            bytes[byteIndex + 1] = (byte) (pixel >>> 16);
            bytes[byteIndex + 2] = (byte) (pixel >>> 8);
            bytes[byteIndex + 3] = (byte) pixel;
        }
        return bytes;
    }

    private static int[] fromArgbBytes(final byte[] bytes, final int expectedPixels) {
        final int[] pixels = new int[expectedPixels];
        for (int pixelIndex = 0, byteIndex = 0; pixelIndex < pixels.length; pixelIndex++, byteIndex += Integer.BYTES) {
            pixels[pixelIndex] = ((bytes[byteIndex] & 0xFF) << 24)
                    | ((bytes[byteIndex + 1] & 0xFF) << 16)
                    | ((bytes[byteIndex + 2] & 0xFF) << 8)
                    | (bytes[byteIndex + 3] & 0xFF);
        }
        return pixels;
    }
}
