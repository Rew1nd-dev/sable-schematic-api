package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Result of applying survival NBT admission and sanitization to a saved block entity tag.
 *
 * @param mode         final load mode
 * @param effectiveTag tag that will actually be loaded during commit, or {@code null}
 */
public record BlueprintNbtLoadDecision(BlueprintNbtLoadMode mode, @Nullable CompoundTag effectiveTag) {
    public BlueprintNbtLoadDecision {
        effectiveTag = effectiveTag != null ? effectiveTag.copy() : null;
    }

    @Override
    public @Nullable CompoundTag effectiveTag() {
        return this.effectiveTag != null ? this.effectiveTag.copy() : null;
    }

    public boolean loadsNbt() {
        return this.effectiveTag != null && (this.mode == BlueprintNbtLoadMode.FULL || this.mode == BlueprintNbtLoadMode.SANITIZED);
    }
}
