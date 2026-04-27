package dev.rew1nd.sableschematicapi.compat.simulated;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockPlaceContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockSaveContext;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintBlockMapper;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

final class SwivelBearingBlueprintMapper implements SableBlueprintBlockMapper {
    private static final String DISCONNECT_ON_PLACE = "sable_schematic_api:swivel_bearing_disconnect";

    private static final String SUB_LEVEL_ID = "SubLevelID";
    private static final String SWIVEL_PLATE = "SwivelPlate";
    private static final String PARENT_POS = "ParentPos";
    private static final String LEGACY_PARENT_POS = "parent";
    private static final String PARENT_SUB_LEVEL_ID = "ParentSubLevelId";

    @Override
    public @Nullable CompoundTag save(final BlueprintBlockSaveContext context, @Nullable final CompoundTag defaultTag) {
        if (defaultTag == null || context.blockEntity() == null) {
            return defaultTag;
        }

        final CompoundTag tag = defaultTag.copy();
        tag.remove(DISCONNECT_ON_PLACE);

        if (context.blockEntity() instanceof final SwivelBearingBlockEntity bearing) {
            clearBearingConnection(tag);
            if (bearing.isAssembled() && !hasCompleteBlueprintConnection(context, bearing)) {
                tag.putBoolean(DISCONNECT_ON_PLACE, true);
            }
        } else if (context.blockEntity() instanceof SwivelBearingPlateBlockEntity) {
            clearPlateConnection(tag);
        }

        return tag;
    }

    @Override
    public void afterLoadBlockEntity(final BlueprintBlockPlaceContext context, final BlockEntity blockEntity, final CompoundTag loadedTag) {
        if (!loadedTag.getBoolean(DISCONNECT_ON_PLACE) || !(blockEntity instanceof final SwivelBearingBlockEntity bearing)) {
            return;
        }

        bearing.setSubLevelID(null);
        bearing.setPlatePos(null);
        final BlockState state = context.level().getBlockState(context.storagePos());
        if (state.hasProperty(SwivelBearingBlock.ASSEMBLED) && state.getValue(SwivelBearingBlock.ASSEMBLED)) {
            context.level().setBlockAndUpdate(context.storagePos(), state.setValue(SwivelBearingBlock.ASSEMBLED, false));
        }
        bearing.setChanged();
    }

    private static boolean hasCompleteBlueprintConnection(final BlueprintBlockSaveContext context, final SwivelBearingBlockEntity bearing) {
        return bearing.getSubLevelID() != null
                && bearing.getPlatePos() != null
                && context.subLevelRef(bearing.getSubLevelID()).isPresent()
                && context.blockRef(bearing.getPlatePos()).isPresent();
    }

    private static void clearBearingConnection(final CompoundTag tag) {
        tag.remove(SUB_LEVEL_ID);
        tag.remove(SWIVEL_PLATE);
    }

    private static void clearPlateConnection(final CompoundTag tag) {
        tag.remove(PARENT_POS);
        tag.remove(LEGACY_PARENT_POS);
        tag.remove(PARENT_SUB_LEVEL_ID);
    }
}
