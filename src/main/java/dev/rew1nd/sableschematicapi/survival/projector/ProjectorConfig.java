package dev.rew1nd.sableschematicapi.survival.projector;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world, server-authoritative projector settings. */
public final class ProjectorConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_RAY_DISTANCE = BUILDER
            .defineInRange("projector.maxRayDistance", 256, 1, 1024);
    public static final ModConfigSpec.IntValue MAX_SCREEN_BLOCKS = BUILDER
            .defineInRange("projector.maxScreenBlocks", 4096, 1, 65536);
    public static final ModConfigSpec.IntValue TERRAIN_CHECKS_PER_TICK = BUILDER
            .comment("Whole-server budget shared fairly by all active projector scans.")
            .defineInRange("projector.terrainChecksPerTick", 4096, 1, 1048576);
    public static final ModConfigSpec.DoubleValue FRONTIER_SPEED = BUILDER
            .defineInRange("projector.frontierSpeedBlocksPerSecond", 8.0D, 0.01D, 1024.0D);
    public static final ModConfigSpec.DoubleValue ANCHOR_TRANSLATION_THRESHOLD = BUILDER
            .defineInRange("projector.anchorTranslationThreshold", 0.25D, 0.0D, 64.0D);
    public static final ModConfigSpec.DoubleValue ANCHOR_ROTATION_THRESHOLD_DEGREES = BUILDER
            .defineInRange("projector.anchorRotationThresholdDegrees", 2.0D, 0.0D, 180.0D);
    public static final ModConfigSpec.DoubleValue HALF_CONE_ANGLE_DEGREES = BUILDER
            .defineInRange("projector.halfConeAngleDegrees", 45.0D, 1.0D, 89.9D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ProjectorConfig() {
    }
}
