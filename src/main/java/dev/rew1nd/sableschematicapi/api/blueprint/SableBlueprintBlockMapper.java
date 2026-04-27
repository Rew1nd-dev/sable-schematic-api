package dev.rew1nd.sableschematicapi.api.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Optional extension point for blocks or block entities that need blueprint-specific
 * NBT cleanup, remapping, or runtime restoration.
 *
 * <p>Register block and block entity mappers from common setup after the optional
 * dependency that owns the block or block entity type has been confirmed loaded.</p>
 */
public interface SableBlueprintBlockMapper {
    /**
     * Gives a mapper the default block entity NBT before it is written to the blueprint.
     *
     * <p>Use this to remove references to the old world, convert storage positions into
     * blueprint-local references, or drop transient data. Returning {@code null} means the
     * block is still saved, but no block entity payload is stored for it.</p>
     *
     * @param context    save-time block context
     * @param defaultTag default block entity tag, or {@code null} when the block has no block entity
     * @return the tag to store in the blueprint, or {@code null} to store no block entity tag
     */
    default @Nullable CompoundTag save(final BlueprintBlockSaveContext context, @Nullable final CompoundTag defaultTag) {
        return defaultTag;
    }

    /**
     * Runs immediately before the copied block entity reads its saved NBT.
     *
     * <p>The supplied tag already has its {@code x/y/z} fields rewritten to the placed
     * storage position. Mutate the tag in place when native block entity loading should see
     * remapped values.</p>
     *
     * @param context placed block context
     * @param tag     mutable block entity tag that will be passed to native loading
     */
    default void beforeLoadBlockEntity(final BlueprintBlockPlaceContext context, final CompoundTag tag) {
    }

    /**
     * Runs after the copied block entity has loaded its NBT.
     *
     * <p>Use this for runtime-only state that cannot be represented safely in NBT, such as
     * reconnecting constraints, refreshing cached object references, or scheduling a task
     * after all block entities are loaded.</p>
     *
     * @param context     placed block context
     * @param blockEntity block entity that was loaded
     * @param loadedTag   copy of the tag passed to native loading
     */
    default void afterLoadBlockEntity(final BlueprintBlockPlaceContext context, final BlockEntity blockEntity, final CompoundTag loadedTag) {
    }
}
