package dev.rew1nd.sableschematicapi.tool.client.preview.stored;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;

public record BlueprintToolStoredPreviewEntry(SableBlueprint.SubLevelData data, Pose3d pose, BlockPos blocksOrigin) {
}
