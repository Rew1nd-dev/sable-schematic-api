package dev.rew1nd.sableschematicapi.client.frontier;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class SableFrontierData {
    private final Vector3f normal = new Vector3f(0.0f, 1.0f, 0.0f);
    private float distance;
    private float gradientWidth = 2.0f;
    private boolean enabled;

    public Vector3fc normal() {
        return this.normal;
    }

    public float distance() {
        return this.distance;
    }

    public float gradientWidth() {
        return this.gradientWidth;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public void setNormal(final Vector3fc normal) {
        this.normal.set(normal);
        if (this.normal.lengthSquared() > 0.0f) {
            this.normal.normalize();
        } else {
            this.normal.set(0.0f, 1.0f, 0.0f);
        }
    }

    public void setDistance(final float distance) {
        this.distance = distance;
    }

    public void setGradientWidth(final float gradientWidth) {
        this.gradientWidth = Math.max(0.0f, gradientWidth);
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
