package dev.rew1nd.sableschematicapi.blueprint.preview;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SableBlueprintPreviewCompressionTest {
    @Test
    void argbPixelsRoundTripLosslessly() {
        final int[] pixels = pixels();

        final byte[] compressed = SableBlueprintPreviewCompression.compress(pixels);
        final int[] restored = SableBlueprintPreviewCompression.decompress(compressed, pixels.length);

        assertArrayEquals(pixels, restored);
        assertTrue(compressed.length < pixels.length * Integer.BYTES);
    }

    @Test
    void truncatedPayloadIsRejected() {
        final int[] pixels = pixels();
        final byte[] compressed = SableBlueprintPreviewCompression.compress(pixels);
        final byte[] truncated = Arrays.copyOf(compressed, compressed.length - 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> SableBlueprintPreviewCompression.decompress(truncated, pixels.length)
        );
    }

    @Test
    void payloadWithTrailingDataIsRejected() {
        final int[] pixels = pixels();
        final byte[] compressed = SableBlueprintPreviewCompression.compress(pixels);
        final byte[] extended = Arrays.copyOf(compressed, compressed.length + 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> SableBlueprintPreviewCompression.decompress(extended, pixels.length)
        );
    }

    @Test
    void payloadExpandingPastExpectedPixelCountIsRejected() {
        final int[] pixels = pixels();
        final byte[] compressed = SableBlueprintPreviewCompression.compress(pixels);

        assertThrows(
                IllegalArgumentException.class,
                () -> SableBlueprintPreviewCompression.decompress(compressed, pixels.length - 1)
        );
    }

    private static int[] pixels() {
        final int[] pixels = new int[512 * 512];
        for (int index = 0; index < pixels.length; index++) {
            if (index % 17 == 0) {
                pixels[index] = 0xFF000000
                        | (index * 53 & 0xFF) << 16
                        | (index * 29 & 0xFF) << 8
                        | index * 11 & 0xFF;
            }
        }
        return pixels;
    }
}
