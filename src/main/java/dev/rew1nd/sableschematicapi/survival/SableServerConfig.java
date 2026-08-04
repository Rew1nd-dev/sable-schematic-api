package dev.rew1nd.sableschematicapi.survival;

import net.neoforged.neoforge.common.ModConfigSpec;

/** All per-world, server-authoritative settings for Sable Photomancy. */
public final class SableServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue CAMERA_MAX_CAPTURE_RANGE;
    public static final ModConfigSpec.IntValue CAMERA_MAX_CAPTURED_BODIES;
    public static final ModConfigSpec.IntValue CAMERA_MAX_BLUEPRINT_BYTES;
    public static final ModConfigSpec.IntValue MANUSCRIPT_MAX_UPLOAD_BYTES;

    public static final ModConfigSpec.IntValue FILM_PRINTER_BLOCKS_PER_TICK;

    public static final ModConfigSpec.IntValue PROJECTOR_MAX_RAY_DISTANCE;
    public static final ModConfigSpec.IntValue PROJECTOR_MAX_SCREEN_BLOCKS;
    public static final ModConfigSpec.IntValue PROJECTOR_TERRAIN_CHECKS_PER_TICK;
    public static final ModConfigSpec.DoubleValue PROJECTOR_FRONTIER_SPEED;
    public static final ModConfigSpec.DoubleValue PROJECTOR_ANCHOR_TRANSLATION_THRESHOLD;
    public static final ModConfigSpec.DoubleValue PROJECTOR_ANCHOR_ROTATION_THRESHOLD_DEGREES;
    public static final ModConfigSpec.DoubleValue PROJECTOR_HALF_CONE_ANGLE_DEGREES;

    public static final ModConfigSpec.IntValue HONEY_WELDER_CAPACITY;
    public static final ModConfigSpec.IntValue HONEY_WELDER_FUEL_PER_BOTTLE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("camera");
        CAMERA_MAX_CAPTURE_RANGE = BUILDER
                .comment("Maximum distance, in blocks, at which a camera may capture a Sable body.")
                .defineInRange("maxCaptureRange", 128, 1, 32_768);
        CAMERA_MAX_CAPTURED_BODIES = BUILDER
                .comment("Maximum number of Sable bodies a single camera capture may contain.")
                .defineInRange("maxCapturedBodies", 128, 1, 4_096);
        CAMERA_MAX_BLUEPRINT_BYTES = BUILDER
                .comment("Maximum compressed byte size of a camera capture.")
                .defineInRange("maxBlueprintBytes", 16 * 1024 * 1024, 1_024, 64 * 1024 * 1024);
        BUILDER.pop();

        BUILDER.push("manuscript");
        MANUSCRIPT_MAX_UPLOAD_BYTES = BUILDER
                .comment("Maximum compressed byte size of one blueprint manuscript upload.")
                .defineInRange("maxUploadBytes", 16 * 1024 * 1024, 1_024, 256 * 1024 * 1024);
        BUILDER.pop();

        BUILDER.push("filmPrinter");
        FILM_PRINTER_BLOCKS_PER_TICK = BUILDER
                .comment("Film printer manufacturing work units completed per server tick.")
                .defineInRange("blocksPerTick", 64, 1, 4096);
        BUILDER.pop();

        BUILDER.push("projector");
        PROJECTOR_MAX_RAY_DISTANCE = BUILDER
                .defineInRange("maxRayDistance", 256, 1, 1024);
        PROJECTOR_MAX_SCREEN_BLOCKS = BUILDER
                .defineInRange("maxScreenBlocks", 4096, 1, 65536);
        PROJECTOR_TERRAIN_CHECKS_PER_TICK = BUILDER
                .comment("Whole-server budget shared fairly by all active projector scans.")
                .defineInRange("terrainChecksPerTick", 4096, 1, 1048576);
        PROJECTOR_FRONTIER_SPEED = BUILDER
                .defineInRange("frontierSpeedBlocksPerSecond", 8.0D, 0.01D, 1024.0D);
        PROJECTOR_ANCHOR_TRANSLATION_THRESHOLD = BUILDER
                .defineInRange("anchorTranslationThreshold", 0.25D, 0.0D, 64.0D);
        PROJECTOR_ANCHOR_ROTATION_THRESHOLD_DEGREES = BUILDER
                .defineInRange("anchorRotationThresholdDegrees", 2.0D, 0.0D, 180.0D);
        PROJECTOR_HALF_CONE_ANGLE_DEGREES = BUILDER
                .defineInRange("halfConeAngleDegrees", 45.0D, 1.0D, 89.9D);
        BUILDER.pop();

        BUILDER.push("honeyWelder");
        HONEY_WELDER_CAPACITY = BUILDER
                .comment("Maximum fuel stored by one honey welder.")
                .defineInRange("capacity", 1000, 1, 1_000_000);
        HONEY_WELDER_FUEL_PER_BOTTLE = BUILDER
                .comment("Fuel granted by one vanilla honey bottle.")
                .defineInRange("fuelPerBottle", 250, 1, 1_000_000);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private SableServerConfig() {
    }
}
