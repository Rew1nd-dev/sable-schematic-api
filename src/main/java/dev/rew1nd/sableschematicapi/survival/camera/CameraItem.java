package dev.rew1nd.sableschematicapi.survival.camera;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Main-hand camera item. Client input owns its viewfinder interactions. */
public final class CameraItem extends Item {
    public CameraItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        // The client viewfinder consumes right click itself. Consuming here is
        // the fallback that prevents vanilla use/swing animation if a click
        // reaches the item interaction path.
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void inventoryTick(final ItemStack stack,
                              final Level level,
                              final Entity entity,
                              final int slotId,
                              final boolean isSelected) {
        if (!level.isClientSide() && entity instanceof final Player player) {
            final CameraState state = CameraState.read(stack);
            if (state.captureId() != null && !state.hasCaptureFor(player.getUUID())) {
                CameraState.write(stack, state.clearCapture());
            }
        }
    }

    @Override
    public void appendHoverText(final ItemStack stack,
                                final TooltipContext context,
                                final List<Component> tooltip,
                                final TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.sable_schematic_api.camera.open")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sable_schematic_api.camera.capture")
                .withStyle(ChatFormatting.GRAY));
    }
}
