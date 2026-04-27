package dev.rew1nd.sableschematicapi.api.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Optional extension point for entities that need blueprint-specific save or
 * placement handling.
 *
 * <p>Entity mappers are useful for moving references stored inside entity NBT from
 * source storage coordinates to blueprint-local coordinates, then back to placed
 * storage coordinates when the blueprint is loaded.</p>
 */
public interface SableBlueprintEntityMapper {
    /**
     * Gives a mapper the default entity NBT before it is written to the blueprint.
     *
     * <p>Returning {@code null} skips the entity entirely. This is the preferred way to
     * exclude player tools, projectiles, or other temporary entities that are not valid
     * persistent blueprint state.</p>
     *
     * @param context    save-time entity context
     * @param defaultTag default entity tag, or {@code null}
     * @return the tag to store, or {@code null} to skip the entity
     */
    default @Nullable CompoundTag save(final BlueprintEntitySaveContext context, @Nullable final CompoundTag defaultTag) {
        return defaultTag;
    }

    /**
     * Runs after the placer rewrites {@code Pos} and removes the source {@code UUID},
     * but before native {@code EntityType.create} is called.
     *
     * <p>Use this to rewrite custom anchors, owner references, or other embedded data.
     * Returning {@code null} skips entity creation.</p>
     *
     * @param context placed entity context
     * @param tag     entity tag prepared for creation
     * @return the tag to create, or {@code null} to skip creation
     */
    default @Nullable CompoundTag beforeCreateEntity(final BlueprintEntityPlaceContext context, final CompoundTag tag) {
        return tag;
    }

    /**
     * Runs after the entity has been created and added to the level.
     *
     * <p>Use this for runtime-only follow-up work that requires the final entity instance.</p>
     *
     * @param context   placed entity context
     * @param entity    created entity
     * @param loadedTag copy of the tag used to create the entity
     */
    default void afterCreateEntity(final BlueprintEntityPlaceContext context, final Entity entity, final CompoundTag loadedTag) {
    }
}
