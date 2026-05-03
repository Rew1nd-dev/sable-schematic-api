package dev.rew1nd.sableschematicapi.survival;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BlueprintTableBlock extends BaseEntityBlock implements BlockUIMenuType.BlockUI {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<BlueprintTableBlock> CODEC = simpleCodec(BlueprintTableBlock::new);

    private static final VoxelShape SHAPE = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0),
            Shapes.box(0.0625, 0.0625, 0.0625, 0.9375, 0.75, 0.9375),
            Shapes.box(0.0, 0.75, 0.0, 1.0, 0.9375, 1.0)
    );

    public BlueprintTableBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new BlueprintTableBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(final BlockState state,
                                  final BlockGetter level,
                                  final BlockPos pos,
                                  final CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(final BlockState state,
                                           final BlockGetter level,
                                           final BlockPos pos,
                                           final CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(final BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack,
                                              final BlockState state,
                                              final Level level,
                                              final BlockPos pos,
                                              final Player player,
                                              final InteractionHand hand,
                                              final BlockHitResult hitResult) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof final BlueprintTableBlockEntity table)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        final ItemStack remaining = table.insertBlueprint(stack);
        if (remaining.getCount() == stack.getCount()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!player.isCreative()) {
            player.setItemInHand(hand, remaining);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state,
                                               final Level level,
                                               final BlockPos pos,
                                               final Player player,
                                               final BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof final ServerPlayer serverPlayer) {
            return BlockUIMenuType.openUI(serverPlayer, pos) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(final BlockState state,
                            final Level level,
                            final BlockPos pos,
                            final BlockState newState,
                            final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof final BlueprintTableBlockEntity table) {
            for (int slot = 0; slot < table.inventory().getSlots(); slot++) {
                final ItemStack stack = table.inventory().getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public ModularUI createUI(final BlockUIMenuType.BlockUIHolder holder) {
        if (holder.player.level().getBlockEntity(holder.pos) instanceof final BlueprintTableBlockEntity table) {
            return BlueprintTableUiRenderer.render(table, holder.player);
        }
        return ModularUI.of(UI.empty(), holder.player);
    }

    @Override
    public boolean stillValid(final BlockUIMenuType.BlockUIHolder holder) {
        return holder.blockState.is(holder.player.level().getBlockState(holder.pos).getBlock())
                && holder.player.distanceToSqr(
                holder.pos.getX() + 0.5D,
                holder.pos.getY() + 0.5D,
                holder.pos.getZ() + 0.5D
        ) <= 64.0D;
    }

    @Override
    public Component getUIDisplayName(final BlockUIMenuType.BlockUIHolder holder) {
        return holder.blockState.getBlock().getName();
    }
}
