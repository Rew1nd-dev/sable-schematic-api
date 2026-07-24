package dev.rew1nd.sableschematicapi.survival.manuscript;

import dev.rew1nd.sableschematicapi.survival.SableSchematicApiDataComponents;
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

public final class BlueprintManuscriptItem extends Item {
    public BlueprintManuscriptItem(final Properties properties) {
        super(properties);
    }

    public static BlueprintManuscriptState state(final ItemStack stack) {
        return stack.getOrDefault(
                SableSchematicApiDataComponents.BLUEPRINT_MANUSCRIPT_STATE.get(),
                BlueprintManuscriptState.EMPTY
        );
    }

    public static void setState(final ItemStack stack, final BlueprintManuscriptState state) {
        stack.set(
                SableSchematicApiDataComponents.BLUEPRINT_MANUSCRIPT_STATE.get(),
                state == null ? BlueprintManuscriptState.EMPTY : state
        );
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level,
                                                   final Player player,
                                                   final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            BlueprintManuscriptClientAccess.open(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(final ItemStack stack,
                                final TooltipContext context,
                                final List<Component> tooltip,
                                final TooltipFlag flag) {
        final BlueprintManuscriptState state = state(stack);
        state.alias().ifPresentOrElse(alias -> {
            tooltip.add(Component.literal(alias.blueprintName()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable(
                    "tooltip.sable_schematic_api.blueprint_manuscript.owner",
                    alias.ownerName()
            ).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                    "tooltip.sable_schematic_api.blueprint_manuscript.rotation",
                    state.rotation().degrees()
            ).withStyle(ChatFormatting.GRAY));
        }, () -> tooltip.add(Component.translatable(
                "tooltip.sable_schematic_api.blueprint_manuscript.empty"
        ).withStyle(ChatFormatting.GRAY)));
    }
}
