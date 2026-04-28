package dev.rew1nd.sableschematicapi.tool.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import dev.rew1nd.sableschematicapi.tool.SableSchematicApiItems;
import dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class BlueprintToolClientEvents {
    private BlueprintToolClientEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BlueprintToolClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(BlueprintToolClientEvents::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(BlueprintToolClientEvents::onRenderGuiPost);
    }

    public static void openUi() {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        if (player == null) {
            return;
        }
        minecraft.setScreen(new ModularUIScreen(
                BlueprintToolUiRenderer.renderClient(player),
                Component.translatable("item.sable_schematic_api.blueprint_tool")
        ));
    }

    private static void onInteractionKey(final InputEvent.InteractionKeyMappingTriggered event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        if (player == null || minecraft.screen != null) {
            return;
        }

        if (event.isAttack() && holdingBlueprintTool(player, InteractionHand.MAIN_HAND)) {
            final BlueprintToolInputIntent intent = player.isShiftKeyDown()
                    ? BlueprintToolInputIntent.SHIFT_ATTACK
                    : BlueprintToolInputIntent.ATTACK;
            applyInputResult(event, BlueprintToolClientSession.handleInput(player, InteractionHand.MAIN_HAND, intent));
            return;
        }

        if (!event.isUseItem() || !holdingBlueprintTool(player, event.getHand())) {
            return;
        }

        final BlueprintToolInputIntent intent = player.isShiftKeyDown()
                ? BlueprintToolInputIntent.SHIFT_USE
                : BlueprintToolInputIntent.USE;
        applyInputResult(event, BlueprintToolClientSession.handleInput(player, event.getHand(), intent));
    }

    private static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        if (player == null || minecraft.screen != null || !holdingBlueprintTool(player)) {
            return;
        }

        while (BlueprintToolKeyMappings.OPEN_BLUEPRINT_TOOL.consumeClick()) {
            openUi();
        }
    }

    private static void onRenderGuiPost(final RenderGuiEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        if (player == null || !holdingBlueprintTool(player)) {
            return;
        }

        final GuiGraphics graphics = event.getGuiGraphics();
        final Font font = minecraft.font;
        final int width = minecraft.getWindow().getGuiScaledWidth();
        final int height = minecraft.getWindow().getGuiScaledHeight();
        final int maxWidth = Math.max(40, width - 20);
        final String text = font.plainSubstrByWidth(BlueprintToolClientSession.hudText().getString(), maxWidth);
        final int textWidth = font.width(text);
        final int x = (width - textWidth) / 2;
        final int y = height - 36;

        graphics.fill(x - 4, y - 3, x + textWidth + 4, y + 10, 0xAA101820);
        graphics.drawString(font, text, x, y, 0xFFE8F0FF, false);
    }

    private static boolean holdingBlueprintTool(final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        return stack.is(SableSchematicApiItems.BLUEPRINT_TOOL.get());
    }

    private static boolean holdingBlueprintTool(final Player player) {
        return holdingBlueprintTool(player, InteractionHand.MAIN_HAND) || holdingBlueprintTool(player, InteractionHand.OFF_HAND);
    }

    private static void applyInputResult(final InputEvent.InteractionKeyMappingTriggered event,
                                         final BlueprintToolInputResult result) {
        if (!result.consumed()) {
            return;
        }

        event.setCanceled(true);
        event.setSwingHand(result.swingHand());
    }
}
