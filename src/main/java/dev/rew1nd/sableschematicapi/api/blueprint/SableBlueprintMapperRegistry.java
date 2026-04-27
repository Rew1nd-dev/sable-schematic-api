package dev.rew1nd.sableschematicapi.api.blueprint;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Registry for blueprint-specific block and block entity data mappers.
 *
 * <p>Registrations are process-global and are expected to happen during common
 * setup. Optional compatibility modules should guard registration with a mod-loaded
 * check before touching classes from the optional dependency.</p>
 */
public final class SableBlueprintMapperRegistry {
    private static final Map<Block, SableBlueprintBlockMapper> BLOCK_MAPPERS = new Object2ObjectOpenHashMap<>();
    private static final Map<BlockEntityType<?>, SableBlueprintBlockMapper> BLOCK_ENTITY_MAPPERS = new Object2ObjectOpenHashMap<>();
    private static final Map<EntityType<?>, SableBlueprintEntityMapper> ENTITY_MAPPERS = new Object2ObjectOpenHashMap<>();

    private SableBlueprintMapperRegistry() {
    }

    /**
     * Registers a mapper for every state of a block.
     *
     * @param block  block to map
     * @param mapper mapper implementation
     */
    public static void register(final Block block, final SableBlueprintBlockMapper mapper) {
        BLOCK_MAPPERS.put(block, mapper);
    }

    /**
     * Registers a mapper for a block entity type.
     *
     * @param type   block entity type to map
     * @param mapper mapper implementation
     */
    public static void register(final BlockEntityType<?> type, final SableBlueprintBlockMapper mapper) {
        BLOCK_ENTITY_MAPPERS.put(type, mapper);
    }

    /**
     * Registers a mapper for an entity type.
     *
     * @param type   entity type to map
     * @param mapper mapper implementation
     */
    public static void register(final EntityType<?> type, final SableBlueprintEntityMapper mapper) {
        ENTITY_MAPPERS.put(type, mapper);
    }

    /**
     * Finds the block mapper for a block state.
     *
     * @param state block state being saved or placed
     * @return mapper registered for the block in the given state, or {@code null}
     */
    public static @Nullable SableBlueprintBlockMapper get(final BlockState state) {
        return BLOCK_MAPPERS.get(state.getBlock());
    }

    /**
     * Finds the mapper for a block entity type.
     *
     * @param type block entity type
     * @return mapper registered for the block entity type, or {@code null}
     */
    public static @Nullable SableBlueprintBlockMapper get(final BlockEntityType<?> type) {
        return BLOCK_ENTITY_MAPPERS.get(type);
    }

    /**
     * Finds the mapper for an entity type.
     *
     * @param type entity type
     * @return mapper registered for the entity type, or {@code null}
     */
    public static @Nullable SableBlueprintEntityMapper get(final EntityType<?> type) {
        return ENTITY_MAPPERS.get(type);
    }

    /**
     * Applies block and block entity save mappers in that order.
     *
     * @param context    save-time block context
     * @param defaultTag default block entity tag, or {@code null}
     * @return mapped block entity tag, or {@code null}
     */
    public static @Nullable CompoundTag save(final BlueprintBlockSaveContext context, @Nullable final CompoundTag defaultTag) {
        CompoundTag tag = defaultTag;

        final SableBlueprintBlockMapper blockMapper = get(context.state());
        if (blockMapper != null) {
            tag = blockMapper.save(context, tag);
        }

        final BlockEntity blockEntity = context.blockEntity();
        if (blockEntity != null) {
            final SableBlueprintBlockMapper blockEntityMapper = get(blockEntity.getType());
            if (blockEntityMapper != null) {
                tag = blockEntityMapper.save(context, tag);
            }
        }

        return tag;
    }

    /**
     * Applies block and block entity pre-load mappers in that order.
     *
     * @param context     placed block context
     * @param blockEntity placed block entity
     * @param tag         mutable block entity tag
     */
    public static void beforeLoadBlockEntity(final BlueprintBlockPlaceContext context, final BlockEntity blockEntity, final CompoundTag tag) {
        final SableBlueprintBlockMapper blockMapper = get(context.state());
        if (blockMapper != null) {
            blockMapper.beforeLoadBlockEntity(context, tag);
        }

        final SableBlueprintBlockMapper blockEntityMapper = get(blockEntity.getType());
        if (blockEntityMapper != null) {
            blockEntityMapper.beforeLoadBlockEntity(context, tag);
        }
    }

    /**
     * Applies block and block entity post-load mappers in that order.
     *
     * @param context     placed block context
     * @param blockEntity placed block entity
     * @param loadedTag   tag passed to native loading
     */
    public static void afterLoadBlockEntity(final BlueprintBlockPlaceContext context, final BlockEntity blockEntity, final CompoundTag loadedTag) {
        final SableBlueprintBlockMapper blockMapper = get(context.state());
        if (blockMapper != null) {
            blockMapper.afterLoadBlockEntity(context, blockEntity, loadedTag);
        }

        final SableBlueprintBlockMapper blockEntityMapper = get(blockEntity.getType());
        if (blockEntityMapper != null) {
            blockEntityMapper.afterLoadBlockEntity(context, blockEntity, loadedTag);
        }
    }

    /**
     * Applies an entity save mapper.
     *
     * @param context    save-time entity context
     * @param defaultTag default entity tag, or {@code null}
     * @return mapped entity tag, or {@code null} to skip the entity
     */
    public static @Nullable CompoundTag saveEntity(final BlueprintEntitySaveContext context, @Nullable final CompoundTag defaultTag) {
        CompoundTag tag = defaultTag;

        final SableBlueprintEntityMapper entityMapper = get(context.entity().getType());
        if (entityMapper != null) {
            tag = entityMapper.save(context, tag);
        }

        return tag;
    }

    /**
     * Applies an entity pre-create mapper.
     *
     * @param context placed entity context
     * @param type    entity type being created
     * @param tag     entity tag prepared for creation
     * @return mapped entity tag, or {@code null} to skip creation
     */
    public static @Nullable CompoundTag beforeCreateEntity(final BlueprintEntityPlaceContext context,
                                                          final EntityType<?> type,
                                                          final CompoundTag tag) {
        final SableBlueprintEntityMapper entityMapper = get(type);
        if (entityMapper != null) {
            return entityMapper.beforeCreateEntity(context, tag);
        }

        return tag;
    }

    /**
     * Applies an entity post-create mapper.
     *
     * @param context   placed entity context
     * @param entity    created entity
     * @param loadedTag tag used to create the entity
     */
    public static void afterCreateEntity(final BlueprintEntityPlaceContext context, final Entity entity, final CompoundTag loadedTag) {
        final SableBlueprintEntityMapper entityMapper = get(entity.getType());
        if (entityMapper != null) {
            entityMapper.afterCreateEntity(context, entity, loadedTag);
        }
    }
}
