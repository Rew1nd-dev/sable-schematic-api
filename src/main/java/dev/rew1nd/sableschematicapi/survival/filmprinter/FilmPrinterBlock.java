package dev.rew1nd.sableschematicapi.survival.filmprinter;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.mojang.serialization.MapCodec;
import dev.rew1nd.sableschematicapi.survival.SableSchematicApiBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import dev.rew1nd.sableschematicapi.survival.SableSchematicApiDataComponents;
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

import java.util.List;

public final class FilmPrinterBlock extends BaseEntityBlock implements BlockUIMenuType.BlockUI {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty BOOK = BooleanProperty.create("book");
    public static final BooleanProperty TAPE = BooleanProperty.create("tape");
    public static final MapCodec<FilmPrinterBlock> CODEC = simpleCodec(FilmPrinterBlock::new);

    public FilmPrinterBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(BOOK, false)
                .setValue(TAPE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new FilmPrinterBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, BOOK, TAPE);
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
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(final Level level,
                                                                            final BlockState state,
                                                                            final BlockEntityType<T> type) {
        return level.isClientSide()
                ? null
                : createTickerHelper(
                        type,
                        SableSchematicApiBlockEntities.FILM_PRINTER.get(),
                        FilmPrinterBlockEntity::serverTick
                );
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
            return BlockUIMenuType.openUI(serverPlayer, pos)
                    ? InteractionResult.CONSUME
                    : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public ModularUI createUI(final BlockUIMenuType.BlockUIHolder holder) {
        if (holder.player.level().getBlockEntity(holder.pos) instanceof final FilmPrinterBlockEntity printer) {
            return FilmPrinterUiRenderer.render(printer, holder.player);
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

    @Override
    protected List<ItemStack> getDrops(final BlockState state, final LootParams.Builder params) {
        final ItemStack stack = new ItemStack(dev.rew1nd.sableschematicapi.tool.SableSchematicApiItems.FILM_PRINTER.get());
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof final FilmPrinterBlockEntity printer
                && printer.hasPortableContents()) {
            stack.set(
                    SableSchematicApiDataComponents.FILM_PRINTER_PORTABLE_STATE.get(),
                    new FilmPrinterPortableState(
                            printer.savePortableData(params.getLevel().registryAccess())
                    )
            );
            stack.set(
                    SableSchematicApiDataComponents.FILM_PRINTER_ITEM_SUMMARY.get(),
                    printer.itemSummary()
            );
            stack.set(DataComponents.MAX_STACK_SIZE, 1);
        }
        return List.of(stack);
    }
}
