package dev.rew1nd.sableschematicapi.api.blueprint;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Registry for blueprint-specific block and block entity data mappers.
 */
public final class SableBlueprintMapperRegistry {
    private static final Map<Block, SableBlueprintBlockMapper> BLOCK_MAPPERS = new Object2ObjectOpenHashMap<>();
    private static final Map<BlockEntityType<?>, SableBlueprintBlockMapper> BLOCK_ENTITY_MAPPERS = new Object2ObjectOpenHashMap<>();

    private SableBlueprintMapperRegistry() {
    }

    public static void register(final Block block, final SableBlueprintBlockMapper mapper) {
        BLOCK_MAPPERS.put(block, mapper);
    }

    public static void register(final BlockEntityType<?> type, final SableBlueprintBlockMapper mapper) {
        BLOCK_ENTITY_MAPPERS.put(type, mapper);
    }

    public static @Nullable SableBlueprintBlockMapper get(final BlockState state) {
        return BLOCK_MAPPERS.get(state.getBlock());
    }

    public static @Nullable SableBlueprintBlockMapper get(final BlockEntityType<?> type) {
        return BLOCK_ENTITY_MAPPERS.get(type);
    }

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
}
