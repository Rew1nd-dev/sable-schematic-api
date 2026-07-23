package dev.rew1nd.sableschematicapi.api.client.preview;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Orthographic preview frame that can project basis-space points into image pixels. */
public record SablePreviewProjectionFrame(
        Vector3d center,
        Vector3d right,
        Vector3d up,
        double halfSpan,
        int resolution
) {
    public SablePreviewProjectionFrame {
        center = new Vector3d(center);
        right = new Vector3d(right).normalize();
        up = new Vector3d(up).normalize();
        if (!(halfSpan > 0.0)) {
            throw new IllegalArgumentException("halfSpan must be positive");
        }
        if (resolution <= 0) {
            throw new IllegalArgumentException("resolution must be positive");
        }
    }

    @Override
    public Vector3d center() {
        return new Vector3d(this.center);
    }

    @Override
    public Vector3d right() {
        return new Vector3d(this.right);
    }

    @Override
    public Vector3d up() {
        return new Vector3d(this.up);
    }

    public Pixel project(final Vector3dc pointInBasis) {
        final Vector3d relative = new Vector3d(pointInBasis).sub(this.center);
        final double diameter = this.halfSpan * 2.0;
        final int x = clamp((int) Math.floor((relative.dot(this.right) + this.halfSpan) / diameter * this.resolution));
        final int y = clamp((int) Math.floor((this.halfSpan - relative.dot(this.up)) / diameter * this.resolution));
        return new Pixel(x, y);
    }

    private int clamp(final int value) {
        return Math.max(0, Math.min(this.resolution - 1, value));
    }

    public record Pixel(int x, int y) {
    }
}
