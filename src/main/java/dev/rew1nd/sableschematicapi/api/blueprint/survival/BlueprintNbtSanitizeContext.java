package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Context supplied to survival block entity NBT sanitizers.
 */
public record BlueprintNbtSanitizeContext(SableBlueprint blueprint,
                                          SableBlueprint.SubLevelData subLevel,
                                          SableBlueprint.BlockData block,
                                          BlueprintBlockRef ref,
                                          BlockState state,
                                          @Nullable CompoundTag rawTag,
                                          @Nullable CompoundTag currentTag,
                                          BlueprintNbtLoadMode mode) {
    public BlueprintNbtSanitizeContext {
        rawTag = rawTag != null ? rawTag.copy() : null;
        currentTag = currentTag != null ? currentTag.copy() : null;
    }

    @Override
    public @Nullable CompoundTag rawTag() {
        return this.rawTag != null ? this.rawTag.copy() : null;
    }

    @Override
    public @Nullable CompoundTag currentTag() {
        return this.currentTag != null ? this.currentTag.copy() : null;
    }
}
