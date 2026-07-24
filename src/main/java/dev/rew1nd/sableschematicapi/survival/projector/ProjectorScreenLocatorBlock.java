package dev.rew1nd.sableschematicapi.survival.projector;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class ProjectorScreenLocatorBlock extends Block {
    public static final DirectionProperty NORMAL = DirectionProperty.create("normal");
    public static final IntegerProperty ROLL = IntegerProperty.create("roll", 0, 3);
    public static final MapCodec<ProjectorScreenLocatorBlock> CODEC =
            simpleCodec(ProjectorScreenLocatorBlock::new);

    public ProjectorScreenLocatorBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(NORMAL, Direction.NORTH)
                .setValue(ROLL, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORMAL, ROLL);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final Direction normal = context.getClickedFace();
        int roll = 0;
        if (normal.getAxis() == Direction.Axis.Y) {
            roll = switch (context.getHorizontalDirection()) {
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> 0;
            };
        }
        return this.defaultBlockState().setValue(NORMAL, normal).setValue(ROLL, roll);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state,
                                               final Level level,
                                               final BlockPos pos,
                                               final Player player,
                                               final BlockHitResult hit) {
        if (!player.isShiftKeyDown()
                || player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty() == false) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.cycle(ROLL), Block.UPDATE_ALL);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
