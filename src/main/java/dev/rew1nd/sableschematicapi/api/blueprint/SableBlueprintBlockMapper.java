package dev.rew1nd.sableschematicapi.api.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Optional extension point for blocks or block entities that need to remap
 * saved data when copied through a Sable blueprint.
 */
public interface SableBlueprintBlockMapper {
    default @Nullable CompoundTag save(final BlueprintBlockSaveContext context, @Nullable final CompoundTag defaultTag) {
        return defaultTag;
    }

    default void beforeLoadBlockEntity(final BlueprintBlockPlaceContext context, final CompoundTag tag) {
    }

    default void afterLoadBlockEntity(final BlueprintBlockPlaceContext context, final BlockEntity blockEntity, final CompoundTag loadedTag) {
    }
}
