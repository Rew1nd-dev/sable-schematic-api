package dev.rew1nd.sableschematicapi.survival.physicsmanager;

import com.lowdragmc.lowdraglib2.gui.factory.HeldItemUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public final class PhysicsManagerItem extends Item implements HeldItemUIMenuType.HeldItemUI {
    public PhysicsManagerItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level,
                                                  final Player player,
                                                  final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (!canUse(player)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("message.sable_schematic_api.physics_manager.permission"),
                        true
                );
            }
            return InteractionResultHolder.fail(stack);
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
        return PhysicsManagerUiRenderer.render(holder);
    }

    @Override
    public boolean stillValid(final HeldItemUIMenuType.HeldItemUIHolder holder) {
        return holder.hand == InteractionHand.MAIN_HAND
                && is(holder.player.getMainHandItem())
                && canUse(holder.player);
    }

    @Override
    public void appendHoverText(final ItemStack stack,
                                final TooltipContext context,
                                final List<Component> tooltipComponents,
                                final TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(
                "tooltip.sable_schematic_api.physics_manager.permission"
        ).withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable(
                "tooltip.sable_schematic_api.physics_manager.main_hand"
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "tooltip.sable_schematic_api.physics_manager.open"
        ).withStyle(ChatFormatting.DARK_GRAY));
    }

    public static boolean canUse(final Player player) {
        return player != null && player.getAbilities().instabuild && player.hasPermissions(2);
    }

    public static boolean is(final ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof PhysicsManagerItem;
    }
}
