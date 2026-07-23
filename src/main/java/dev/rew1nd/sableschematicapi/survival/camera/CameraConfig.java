package dev.rew1nd.sableschematicapi.survival.camera;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side limits for camera captures. */
public final class CameraConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_CAPTURE_RANGE = BUILDER
            .comment("Maximum distance, in blocks, at which a camera may capture a Sable body.")
            .defineInRange("camera.maxCaptureRange", 128, 1, 32_768);
    public static final ModConfigSpec.IntValue MAX_CAPTURED_BODIES = BUILDER
            .comment("Maximum number of Sable bodies a single camera capture may contain.")
            .defineInRange("camera.maxCapturedBodies", 128, 1, 4_096);
    public static final ModConfigSpec.IntValue MAX_BLUEPRINT_BYTES = BUILDER
            .comment("Maximum compressed byte size of a camera capture.")
            .defineInRange("camera.maxBlueprintBytes", 16 * 1024 * 1024, 1_024, 64 * 1024 * 1024);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private CameraConfig() {
    }
}
