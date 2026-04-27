package dev.rew1nd.sableschematicapi.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class BlueprintToolItem extends Item {
    public BlueprintToolItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(final ItemStack stack,
                                final TooltipContext context,
                                final List<Component> tooltipComponents,
                                final TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.sable_schematic_api.blueprint_tool.line1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.sable_schematic_api.blueprint_tool.line2").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "tooltip.sable_schematic_api.blueprint_tool.line3",
                Component.keybind("key.sable_schematic_api.blueprint_tool.open")
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.sable_schematic_api.blueprint_tool.line4").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.sable_schematic_api.blueprint_tool.line5").withStyle(ChatFormatting.YELLOW));
    }
}
