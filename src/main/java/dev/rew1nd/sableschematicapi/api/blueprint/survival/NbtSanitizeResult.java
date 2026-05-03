package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Result returned by a block entity NBT sanitizer.
 */
public record NbtSanitizeResult(BlueprintNbtLoadMode mode, @Nullable CompoundTag tag) {
    public NbtSanitizeResult {
        tag = tag != null ? tag.copy() : null;
    }

    public static NbtSanitizeResult pass(final BlueprintNbtSanitizeContext context) {
        return new NbtSanitizeResult(context.mode(), context.currentTag());
    }

    @Override
    public @Nullable CompoundTag tag() {
        return this.tag != null ? this.tag.copy() : null;
    }
}
