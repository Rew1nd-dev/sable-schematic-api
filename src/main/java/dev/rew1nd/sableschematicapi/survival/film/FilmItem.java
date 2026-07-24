package dev.rew1nd.sableschematicapi.survival.film;

import dev.rew1nd.sableschematicapi.survival.SableSchematicApiDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class FilmItem extends Item {
    public FilmItem(final Properties properties) {
        super(properties);
    }

    public static FilmState state(final ItemStack stack) {
        return stack.getOrDefault(SableSchematicApiDataComponents.FILM_STATE.get(), FilmState.EMPTY);
    }

    public static void setState(final ItemStack stack, final FilmState state) {
        stack.set(SableSchematicApiDataComponents.FILM_STATE.get(), state == null ? FilmState.EMPTY : state);
    }

    @Override
    public void appendHoverText(final ItemStack stack,
                                final TooltipContext context,
                                final List<Component> tooltip,
                                final TooltipFlag flag) {
        final FilmState state = state(stack);
        if (!state.isRecorded()) {
            tooltip.add(Component.translatable("tooltip.sable_schematic_api.film.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        state.snapshot().ifPresent(snapshot -> {
            tooltip.add(Component.literal(snapshot.name()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable(
                    "tooltip.sable_schematic_api.film.author",
                    snapshot.author().isBlank() ? "-" : snapshot.author()
            ).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                    "tooltip.sable_schematic_api.film.summary",
                    snapshot.summary().blocks(),
                    snapshot.summary().entities()
            ).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                    "tooltip.sable_schematic_api.film.rotation",
                    state.rotation().degrees()
            ).withStyle(ChatFormatting.GRAY));
        });
        state.objectSha256().ifPresent(sha256 -> tooltip.add(Component.translatable(
                "tooltip.sable_schematic_api.film.hash",
                sha256.substring(0, 12)
        ).withStyle(ChatFormatting.DARK_GRAY)));
    }
}
