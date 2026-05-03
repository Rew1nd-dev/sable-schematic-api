package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Effective per-block payload used by survival placement and material rules.
 */
public record BlueprintBuildBlockPayload(BlueprintBlockRef ref,
                                         SableBlueprint.SubLevelData subLevel,
                                         SableBlueprint.BlockData block,
                                         BlockState state,
                                         @Nullable CompoundTag rawBlockEntityTag,
                                         BlueprintNbtLoadDecision nbtDecision) {
    public BlueprintBuildBlockPayload {
        rawBlockEntityTag = rawBlockEntityTag != null ? rawBlockEntityTag.copy() : null;
    }

    @Override
    public @Nullable CompoundTag rawBlockEntityTag() {
        return this.rawBlockEntityTag != null ? this.rawBlockEntityTag.copy() : null;
    }

    public @Nullable CompoundTag effectiveBlockEntityTag() {
        return this.nbtDecision.effectiveTag();
    }
}
