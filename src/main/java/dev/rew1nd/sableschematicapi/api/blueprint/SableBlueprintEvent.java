package dev.rew1nd.sableschematicapi.api.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Global blueprint hook for systems that store data outside individual block
 * entities or need to coordinate more than one saved object.
 *
 * <p>Events receive one namespaced {@link CompoundTag} inside the blueprint's
 * {@code global_extra_data}. Use this sidecar for relationships such as ropes,
 * glue regions, constraints, or manager-owned state that cannot be represented
 * safely by a single block or entity mapper.</p>
 */
public interface SableBlueprintEvent {
    /**
     * Stable namespaced key for this event's sidecar data.
     *
     * @return the key used under blueprint {@code global_extra_data}
     */
    ResourceLocation id();

    /**
     * Runs after sub-level frames are selected and before block data is read.
     *
     * @param session save session
     * @param data    mutable event sidecar tag
     */
    default void onSaveBeforeBlocks(final BlueprintSaveSession session, final CompoundTag data) {
    }

    /**
     * Runs after blocks and block entity payloads are saved.
     *
     * <p>This phase can inspect {@link BlueprintSaveSession#savedBlocks()} and convert
     * source positions into {@link BlueprintBlockRef} values.</p>
     *
     * @param session save session
     * @param data    mutable event sidecar tag
     */
    default void onSaveAfterBlocks(final BlueprintSaveSession session, final CompoundTag data) {
    }

    /**
     * Runs after entities have been saved and mappers have had a chance to transform them.
     *
     * @param session save session
     * @param data    mutable event sidecar tag
     */
    default void onSaveAfterEntities(final BlueprintSaveSession session, final CompoundTag data) {
    }

    /**
     * Runs after placed sub-levels have been allocated and before block states are written.
     *
     * @param session placement session
     * @param data    event sidecar tag copied from the blueprint
     */
    default void onPlaceBeforeBlocks(final BlueprintPlaceSession session, final CompoundTag data) {
    }

    /**
     * Runs after block entities have loaded and deferred block-entity mapper tasks have run.
     *
     * <p>This is the usual phase for reconnecting multi-block relationships because
     * {@link BlueprintPlaceSession#placedBlocks()} can expose loaded block entities.</p>
     *
     * @param session placement session
     * @param data    event sidecar tag copied from the blueprint
     */
    default void onPlaceAfterBlockEntities(final BlueprintPlaceSession session, final CompoundTag data) {
    }

    /**
     * Runs after block notifications and entity placement complete.
     *
     * @param session placement session
     * @param data    event sidecar tag copied from the blueprint
     */
    default void onPlaceAfterBlocks(final BlueprintPlaceSession session, final CompoundTag data) {
    }
}
