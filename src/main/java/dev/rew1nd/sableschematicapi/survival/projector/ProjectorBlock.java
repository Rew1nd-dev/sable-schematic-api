package dev.rew1nd.sableschematicapi.survival.projector;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.mojang.serialization.MapCodec;
import dev.rew1nd.sableschematicapi.survival.SableSchematicApiBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class ProjectorBlock extends BaseEntityBlock implements BlockUIMenuType.BlockUI {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final MapCodec<ProjectorBlock> CODEC = simpleCodec(ProjectorBlock::new);

    public ProjectorBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(final BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ProjectorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(final Level level,
                                                                            final BlockState state,
                                                                            final BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(
                type,
                SableSchematicApiBlockEntities.PROJECTOR.get(),
                ProjectorBlockEntity::serverTick
        );
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state,
                                               final Level level,
                                               final BlockPos pos,
                                               final Player player,
                                               final BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return player instanceof ServerPlayer serverPlayer && BlockUIMenuType.openUI(serverPlayer, pos)
                ? InteractionResult.CONSUME
                : InteractionResult.PASS;
    }

    @Override
    protected void neighborChanged(final BlockState state,
                                   final Level level,
                                   final BlockPos pos,
                                   final Block neighborBlock,
                                   final BlockPos neighborPos,
                                   final boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        final boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) {
            return;
        }
        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        if (powered && level.getBlockEntity(pos) instanceof final ProjectorBlockEntity projector) {
            projector.activateFromRedstone();
        }
    }

    @Override
    protected void onRemove(final BlockState oldState,
                            final Level level,
                            final BlockPos pos,
                            final BlockState newState,
                            final boolean movedByPiston) {
        if (!oldState.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof final ProjectorBlockEntity projector) {
            projector.abortForRemoval();
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D, projector.inventory().getStackInSlot(0));
            projector.inventory().setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY);
        }
        super.onRemove(oldState, level, pos, newState, movedByPiston);
    }

    @Override
    public ModularUI createUI(final BlockUIMenuType.BlockUIHolder holder) {
        if (holder.player.level().getBlockEntity(holder.pos) instanceof final ProjectorBlockEntity projector) {
            return ProjectorUiRenderer.render(projector, holder.player);
        }
        return ModularUI.of(UI.empty(), holder.player);
    }

    @Override
    public boolean stillValid(final BlockUIMenuType.BlockUIHolder holder) {
        return holder.blockState.is(holder.player.level().getBlockState(holder.pos).getBlock())
                && holder.player.distanceToSqr(holder.pos.getCenter()) <= 64.0D;
    }

    @Override
    public Component getUIDisplayName(final BlockUIMenuType.BlockUIHolder holder) {
        return holder.blockState.getBlock().getName();
    }
}
