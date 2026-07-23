package dev.rew1nd.sableschematicapi.api.client.preview;

import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Camera axes and offset used by client-side Sable preview captures. */
public enum SablePreviewView {
    ISO_XP_ZP(new Vector3d(1.0, 1.0, 1.0), null),
    ISO_XP_ZN(new Vector3d(1.0, 1.0, -1.0), null),
    ISO_XN_ZP(new Vector3d(-1.0, 1.0, 1.0), null),
    ISO_XN_ZN(new Vector3d(-1.0, 1.0, -1.0), null),
    TOP_Y(new Vector3d(0.0, 1.0, 0.0), new Vector3d(0.0, 0.0, -1.0));

    private final Vector3d cameraOffsetDirection;
    private final Vector3d forward;
    private final Vector3d up;
    private final Vector3d right;

    SablePreviewView(final Vector3dc cameraOffsetDirection, final Vector3dc fixedUp) {
        this.cameraOffsetDirection = new Vector3d(cameraOffsetDirection);
        this.forward = new Vector3d(this.cameraOffsetDirection).negate().normalize();
        this.up = fixedUp != null
                ? new Vector3d(fixedUp).normalize()
                : new Vector3d(0.0, 1.0, 0.0)
                        .sub(new Vector3d(this.forward).mul(this.forward.y()))
                        .normalize();
        this.right = new Vector3d(this.forward).cross(this.up).normalize();
    }

    public Vector3dc cameraOffsetDirection() {
        return new Vector3d(this.cameraOffsetDirection);
    }

    public Vector3dc forward() {
        return new Vector3d(this.forward);
    }

    public Vector3dc up() {
        return new Vector3d(this.up);
    }

    public Vector3dc right() {
        return new Vector3d(this.right);
    }

    public static SablePreviewView fromStoredView(final SableBlueprintPreview.View view) {
        return valueOf(view.name());
    }
}
