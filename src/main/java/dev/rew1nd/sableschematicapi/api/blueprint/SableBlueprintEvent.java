package dev.rew1nd.sableschematicapi.api.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Global blueprint hook for systems that store data outside individual block
 * entities.
 */
public interface SableBlueprintEvent {
    ResourceLocation id();

    default void onSaveBeforeBlocks(final BlueprintSaveSession session, final CompoundTag data) {
    }

    default void onSaveAfterBlocks(final BlueprintSaveSession session, final CompoundTag data) {
    }

    default void onSaveAfterEntities(final BlueprintSaveSession session, final CompoundTag data) {
    }

    default void onPlaceBeforeBlocks(final BlueprintPlaceSession session, final CompoundTag data) {
    }

    default void onPlaceAfterBlockEntities(final BlueprintPlaceSession session, final CompoundTag data) {
    }

    default void onPlaceAfterBlocks(final BlueprintPlaceSession session, final CompoundTag data) {
    }
}
