package dev.rew1nd.sableschematicapi.survival.honeywelder;

import com.lowdragmc.lowdraglib2.gui.factory.HeldItemUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import dev.rew1nd.sableschematicapi.api.honeywelder.HoneyWelderConsumers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public final class HoneyWelderItem extends Item implements HeldItemUIMenuType.HeldItemUI {
    private static final int FUEL_BAR_COLOR = 0xE3A62F;

    public HoneyWelderItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level,
                                                   final Player player,
                                                   final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide() && player instanceof final ServerPlayer serverPlayer) {
            HeldItemUIMenuType.openUI(serverPlayer, hand);
        }
        return new InteractionResultHolder<>(
                level.isClientSide() ? InteractionResult.CONSUME : InteractionResult.SUCCESS,
                stack
        );
    }

    @Override
    public ModularUI createUI(final HeldItemUIMenuType.HeldItemUIHolder holder) {
        return HoneyWelderUiRenderer.render(holder);
    }

    @Override
    public boolean stillValid(final HeldItemUIMenuType.HeldItemUIHolder holder) {
        return holder.hand == InteractionHand.MAIN_HAND && is(holder.player.getMainHandItem());
    }

    @Override
    public void appendHoverText(final ItemStack stack,
                                final TooltipContext context,
                                final List<Component> tooltipComponents,
                                final TooltipFlag tooltipFlag) {
        final HoneyWelderState state = HoneyWelderState.read(stack);
        tooltipComponents.add(Component.translatable(
                "tooltip.sable_schematic_api.honey_welder.fuel",
                state.fuel(),
                HoneyWelderConfig.capacity()
        ).withStyle(ChatFormatting.GOLD));
        final Component consumer = state.selectedConsumer()
                .flatMap(HoneyWelderConsumers::find)
                .<Component>map(value -> value.label().copy())
                .orElseGet(() -> state.selectedConsumer()
                        .<Component>map(id -> Component.translatable(
                                "tooltip.sable_schematic_api.honey_welder.consumer_missing",
                                id
                        ))
                        .orElseGet(() -> Component.translatable(
                                "tooltip.sable_schematic_api.honey_welder.consumer_none"
                        )));
        tooltipComponents.add(Component.translatable(
                "tooltip.sable_schematic_api.honey_welder.consumer",
                consumer
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "tooltip.sable_schematic_api.honey_welder.open"
        ).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(final ItemStack stack) {
        return HoneyWelderState.read(stack).fuel() > 0;
    }

    @Override
    public int getBarWidth(final ItemStack stack) {
        return Math.round(13.0F * Mth.clamp(
                HoneyWelderState.read(stack).fuel() / (float) HoneyWelderConfig.capacity(),
                0.0F,
                1.0F
        ));
    }

    @Override
    public int getBarColor(final ItemStack stack) {
        return FUEL_BAR_COLOR;
    }

    public static boolean is(final ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof HoneyWelderItem;
    }
}
