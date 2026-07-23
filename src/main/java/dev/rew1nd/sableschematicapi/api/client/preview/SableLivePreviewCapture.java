package dev.rew1nd.sableschematicapi.api.client.preview;

import java.util.Arrays;

/** Pixels and projection metadata for one rendered live preview view. */
public record SableLivePreviewCapture(
        int resolution,
        int basisSubLevelId,
        int[] pixels,
        SablePreviewProjectionFrame projectionFrame
) {
    public SableLivePreviewCapture {
        if (resolution <= 0) {
            throw new IllegalArgumentException("resolution must be positive");
        }
        if (pixels == null || pixels.length != resolution * resolution) {
            throw new IllegalArgumentException("preview pixel count does not match resolution");
        }
        pixels = Arrays.copyOf(pixels, pixels.length);
        if (projectionFrame == null || projectionFrame.resolution() != resolution) {
            throw new IllegalArgumentException("projection frame does not match resolution");
        }
    }

    @Override
    public int[] pixels() {
        return Arrays.copyOf(this.pixels, this.pixels.length);
    }
}
