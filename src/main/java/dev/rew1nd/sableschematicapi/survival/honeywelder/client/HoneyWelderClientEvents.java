package dev.rew1nd.sableschematicapi.survival.honeywelder.client;

import dev.rew1nd.sableschematicapi.api.honeywelder.client.HoneyWelderClientConsumer;
import dev.rew1nd.sableschematicapi.api.honeywelder.client.HoneyWelderInputIntent;
import dev.rew1nd.sableschematicapi.api.honeywelder.client.HoneyWelderInputResult;
import dev.rew1nd.sableschematicapi.survival.honeywelder.HoneyWelderItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class HoneyWelderClientEvents {
    private static final int MISSING_NOTICE_COOLDOWN_TICKS = 40;
    private static boolean attackPressHandled;
    private static boolean usePressHandled;
    private static int missingNoticeCooldown;

    private HoneyWelderClientEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(HoneyWelderClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(HoneyWelderClientEvents::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(HoneyWelderClientEvents::onRenderGuiPost);
    }

    private static void onInteractionKey(final InputEvent.InteractionKeyMappingTriggered event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        if (player == null || minecraft.screen != null || !HoneyWelderItem.is(player.getMainHandItem())) {
            return;
        }

        if (event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND && player.isShiftKeyDown()) {
            if (HoneyWelderClientSession.active()) {
                HoneyWelderClientSession.requestCancel();
            }
            return;
        }

        if (event.isAttack()) {
            if (!consumeAttackPress()) {
                apply(event, HoneyWelderInputResult.consume(false));
                return;
            }
            if (player.isShiftKeyDown() && HoneyWelderClientSession.active()) {
                HoneyWelderClientSession.requestCancel();
                apply(event, HoneyWelderInputResult.consume(true));
                return;
            }
            dispatch(event, player, HoneyWelderInputIntent.ATTACK);
            return;
        }

        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!consumeUsePress()) {
            apply(event, HoneyWelderInputResult.consume(false));
            return;
        }
        dispatch(event, player, HoneyWelderInputIntent.USE);
    }

    private static void dispatch(final InputEvent.InteractionKeyMappingTriggered event,
                                 final Player player,
                                 final HoneyWelderInputIntent intent) {
        final HoneyWelderClientConsumer consumer = HoneyWelderClientSession.active()
                ? HoneyWelderClientSession.activeProvider().orElse(null)
                : HoneyWelderClientSession.selectedProvider(player).orElse(null);
        if (consumer == null) {
            notifyUnavailable(player);
            apply(event, HoneyWelderInputResult.consume(false));
            return;
        }
        final HoneyWelderInputResult result = consumer.handleInput(player, intent);
        apply(event, result == null ? HoneyWelderInputResult.PASS : result);
    }

    private static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        updatePressGuards(minecraft);
        if (missingNoticeCooldown > 0) {
            missingNoticeCooldown--;
        }

        final Player player = minecraft.player;
        if (player == null) {
            return;
        }
        if (HoneyWelderClientSession.active()
                && (!HoneyWelderItem.is(player.getMainHandItem())
                || player.getInventory().selected != HoneyWelderClientSession.activeHotbarSlot())) {
            HoneyWelderClientSession.cancelForItemChange();
            return;
        }
        if (minecraft.screen == null) {
            HoneyWelderClientSession.activeProvider().ifPresent(provider -> provider.renderPreview(player));
        }
    }

    private static void onRenderGuiPost(final RenderGuiEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        if (player == null || !HoneyWelderItem.is(player.getMainHandItem())) {
            return;
        }
        final Component component = HoneyWelderClientSession.activeProvider()
                .map(provider -> provider.hudText(player))
                .orElse(Component.empty());
        if (component == null || component.getString().isBlank()) {
            return;
        }
        final GuiGraphics graphics = event.getGuiGraphics();
        final Font font = minecraft.font;
        final int width = minecraft.getWindow().getGuiScaledWidth();
        final int height = minecraft.getWindow().getGuiScaledHeight();
        final String text = font.plainSubstrByWidth(component.getString(), Math.max(40, width - 20));
        final int textWidth = font.width(text);
        final int x = (width - textWidth) / 2;
        final int y = height - 36;
        graphics.fill(x - 4, y - 3, x + textWidth + 4, y + 10, 0xAA101820);
        graphics.drawString(font, text, x, y, 0xFFFFD477, false);
    }

    private static void notifyUnavailable(final Player player) {
        if (missingNoticeCooldown > 0) {
            return;
        }
        missingNoticeCooldown = MISSING_NOTICE_COOLDOWN_TICKS;
        player.displayClientMessage(Component.translatable(
                "message.sable_schematic_api.honey_welder.unavailable"
        ).withStyle(ChatFormatting.YELLOW), true);
    }

    private static void updatePressGuards(final Minecraft minecraft) {
        if (minecraft.screen != null) {
            attackPressHandled = false;
            usePressHandled = false;
            return;
        }
        if (!minecraft.options.keyAttack.isDown()) {
            attackPressHandled = false;
        }
        if (!minecraft.options.keyUse.isDown()) {
            usePressHandled = false;
        }
    }

    private static boolean consumeAttackPress() {
        if (attackPressHandled) {
            return false;
        }
        attackPressHandled = true;
        return true;
    }

    private static boolean consumeUsePress() {
        if (usePressHandled) {
            return false;
        }
        usePressHandled = true;
        return true;
    }

    private static void apply(final InputEvent.InteractionKeyMappingTriggered event,
                              final HoneyWelderInputResult result) {
        if (result == null || !result.consumed()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(result.swingHand());
    }
}
