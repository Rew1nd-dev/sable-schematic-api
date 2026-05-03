package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtLoadDecision;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtLoadMode;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtPolicyTags;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintNbtSanitizeContext;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.NbtSanitizeResult;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Survival NBT admission policy for blueprint block entity payloads.
 */
public final class BlueprintNbtPolicies {
    private static BlueprintNbtLoadMode defaultBlockEntityMode = BlueprintNbtLoadMode.SANITIZED;

    private BlueprintNbtPolicies() {
    }

    public static void setDefaultBlockEntityMode(final BlueprintNbtLoadMode mode) {
        defaultBlockEntityMode = mode;
    }

    public static BlueprintNbtLoadDecision resolve(final SableBlueprint blueprint,
                                                   final SableBlueprint.SubLevelData subLevel,
                                                   final SableBlueprint.BlockData block,
                                                   final BlockState state,
                                                   @Nullable final CompoundTag rawTag) {
        if (state.is(BlueprintNbtPolicyTags.BUILD_DENIED)) {
            return new BlueprintNbtLoadDecision(BlueprintNbtLoadMode.DENY, null);
        }

        if (rawTag == null) {
            return new BlueprintNbtLoadDecision(BlueprintNbtLoadMode.NONE, null);
        }

        final BlueprintNbtLoadMode mode = modeFor(state);
        if (mode == BlueprintNbtLoadMode.NONE || mode == BlueprintNbtLoadMode.DENY) {
            return new BlueprintNbtLoadDecision(mode, null);
        }

        final BlueprintBlockRef ref = new BlueprintBlockRef(subLevel.id(), block.localPos());
        final NbtSanitizeResult sanitized = BlueprintNbtSanitizers.sanitize(new BlueprintNbtSanitizeContext(
                blueprint,
                subLevel,
                block,
                ref,
                state,
                rawTag,
                rawTag,
                mode
        ));
        return new BlueprintNbtLoadDecision(sanitized.mode(), sanitized.tag());
    }

    private static BlueprintNbtLoadMode modeFor(final BlockState state) {
        if (state.is(BlueprintNbtPolicyTags.NO_NBT_LOAD)) {
            return BlueprintNbtLoadMode.NONE;
        }
        if (state.is(BlueprintNbtPolicyTags.FULL_NBT_LOAD)) {
            return BlueprintNbtLoadMode.FULL;
        }
        if (state.is(BlueprintNbtPolicyTags.SANITIZED_NBT_LOAD)) {
            return BlueprintNbtLoadMode.SANITIZED;
        }

        return defaultBlockEntityMode;
    }
}
