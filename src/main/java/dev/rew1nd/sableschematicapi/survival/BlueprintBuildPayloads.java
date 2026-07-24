package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintBuildBlockPayload;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtLoadDecision;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the effective survival payload for a blueprint block.
 *
 * <p>Both placement and preflight quoting must use this path so palette
 * validation, raw block-entity lookup, and NBT policy decisions cannot drift.</p>
 */
public final class BlueprintBuildPayloads {
    private BlueprintBuildPayloads() {
    }

    public static BlueprintBuildBlockPayload resolve(final SableBlueprint blueprint,
                                                     final SableBlueprint.SubLevelData subLevel,
                                                     final SableBlueprint.BlockData block) {
        if (block.paletteId() < 0 || block.paletteId() >= subLevel.blockPalette().size()) {
            throw new IllegalStateException("Invalid blueprint palette id " + block.paletteId());
        }

        final BlockState state = subLevel.blockPalette().get(block.paletteId());
        final CompoundTag rawTag = rawBlockEntityTag(subLevel, block);
        final BlueprintNbtLoadDecision decision =
                BlueprintNbtPolicies.resolve(blueprint, subLevel, block, state, rawTag);
        return new BlueprintBuildBlockPayload(
                new BlueprintBlockRef(subLevel.id(), block.localPos()),
                subLevel,
                block,
                state,
                rawTag,
                decision
        );
    }

    private static @Nullable CompoundTag rawBlockEntityTag(final SableBlueprint.SubLevelData subLevel,
                                                           final SableBlueprint.BlockData block) {
        if (!block.hasBlockEntityData()) {
            return null;
        }
        if (block.blockEntityDataId() < 0 || block.blockEntityDataId() >= subLevel.blockEntities().size()) {
            return null;
        }
        return subLevel.blockEntities().get(block.blockEntityDataId()).copy();
    }
}
