package dev.rew1nd.sableschematicapi.survival.filmprinter;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import dev.rew1nd.sableschematicapi.survival.SableSchematicApiDataComponents;

import java.util.List;

public final class FilmPrinterItem extends BlockItem {
    public FilmPrinterItem(final Block block, final Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(final BlockPlaceContext context) {
        final FilmPrinterPortableState portable = context.getItemInHand().get(
                SableSchematicApiDataComponents.FILM_PRINTER_PORTABLE_STATE.get()
        );
        final InteractionResult result = super.place(context);
        if (portable != null && result.consumesAction() && !context.getLevel().isClientSide()
                && context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof final FilmPrinterBlockEntity printer) {
            printer.restorePortableData(portable.data(), context.getLevel().registryAccess());
        }
        return result;
    }

    @Override
    public void appendHoverText(final ItemStack stack,
                                final TooltipContext context,
                                final List<Component> tooltip,
                                final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        final FilmPrinterItemSummary summary = stack.get(
                SableSchematicApiDataComponents.FILM_PRINTER_ITEM_SUMMARY.get()
        );
        if (summary == null) {
            return;
        }
        final String phase = summary.phase();
        final String name = summary.taskName();
        if (!name.isBlank()) {
            tooltip.add(Component.translatable(
                    "tooltip.sable_schematic_api.film_printer.task",
                    name
            ).withStyle(ChatFormatting.AQUA));
        }
        if (!phase.isBlank() && !phase.equals(FilmPrinterPhase.IDLE.name())) {
            tooltip.add(Component.translatable(
                    "tooltip.sable_schematic_api.film_printer.state",
                    phase
            ).withStyle(ChatFormatting.GRAY));
        }
        final long total = summary.totalWork();
        if (total > 0L) {
            tooltip.add(Component.translatable(
                    "tooltip.sable_schematic_api.film_printer.progress",
                    summary.completedWork(),
                    total
            ).withStyle(ChatFormatting.GRAY));
        }
        if (summary.refundItems() > 0) {
            tooltip.add(Component.translatable(
                    "tooltip.sable_schematic_api.film_printer.refund",
                    summary.refundItems()
            ).withStyle(ChatFormatting.GOLD));
        }
        if (summary.hasInventory() && name.isBlank() && summary.refundItems() == 0) {
            tooltip.add(Component.translatable(
                    "tooltip.sable_schematic_api.film_printer.contains_items"
            ).withStyle(ChatFormatting.GRAY));
        }
    }
}
