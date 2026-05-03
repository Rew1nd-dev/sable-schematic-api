package dev.rew1nd.sableschematicapi.tool.client.preview.stored;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;

record BlueprintToolLivePose(Pose3d pose, BlockPos blocksOrigin) {
}
