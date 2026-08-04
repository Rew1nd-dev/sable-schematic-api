package dev.rew1nd.sableschematicapi.survival.camera;

import dev.rew1nd.sableschematicapi.survival.SableServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side limits for camera captures. */
public final class CameraConfig {
    public static final ModConfigSpec.IntValue MAX_CAPTURE_RANGE =
            SableServerConfig.CAMERA_MAX_CAPTURE_RANGE;
    public static final ModConfigSpec.IntValue MAX_CAPTURED_BODIES =
            SableServerConfig.CAMERA_MAX_CAPTURED_BODIES;
    public static final ModConfigSpec.IntValue MAX_BLUEPRINT_BYTES =
            SableServerConfig.CAMERA_MAX_BLUEPRINT_BYTES;
    public static final ModConfigSpec.IntValue MAX_MANUSCRIPT_UPLOAD_BYTES =
            SableServerConfig.MANUSCRIPT_MAX_UPLOAD_BYTES;

    private CameraConfig() {
    }
}
