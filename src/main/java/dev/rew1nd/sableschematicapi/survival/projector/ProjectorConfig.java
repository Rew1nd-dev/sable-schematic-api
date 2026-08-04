package dev.rew1nd.sableschematicapi.survival.projector;

import dev.rew1nd.sableschematicapi.survival.SableServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world, server-authoritative projector settings. */
public final class ProjectorConfig {
    public static final ModConfigSpec.IntValue MAX_RAY_DISTANCE =
            SableServerConfig.PROJECTOR_MAX_RAY_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_SCREEN_BLOCKS =
            SableServerConfig.PROJECTOR_MAX_SCREEN_BLOCKS;
    public static final ModConfigSpec.IntValue TERRAIN_CHECKS_PER_TICK =
            SableServerConfig.PROJECTOR_TERRAIN_CHECKS_PER_TICK;
    public static final ModConfigSpec.DoubleValue FRONTIER_SPEED =
            SableServerConfig.PROJECTOR_FRONTIER_SPEED;
    public static final ModConfigSpec.DoubleValue ANCHOR_TRANSLATION_THRESHOLD =
            SableServerConfig.PROJECTOR_ANCHOR_TRANSLATION_THRESHOLD;
    public static final ModConfigSpec.DoubleValue ANCHOR_ROTATION_THRESHOLD_DEGREES =
            SableServerConfig.PROJECTOR_ANCHOR_ROTATION_THRESHOLD_DEGREES;
    public static final ModConfigSpec.DoubleValue HALF_CONE_ANGLE_DEGREES =
            SableServerConfig.PROJECTOR_HALF_CONE_ANGLE_DEGREES;

    private ProjectorConfig() {
    }
}
