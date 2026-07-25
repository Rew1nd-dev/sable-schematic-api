package dev.rew1nd.sableschematicapi.api.honeywelder.client;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.rew1nd.sableschematicapi.api.honeywelder.HoneyWelderCancelReason;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public interface HoneyWelderClientConsumer {
    ResourceLocation id();

    UIElement createPanel(Player player);

    default void sessionStarted(final Player player, final ResourceLocation action, final int currentFuel) {
    }

    default void sessionEnded(final Player player,
                              final HoneyWelderCancelReason reason,
                              final boolean finished,
                              final String message) {
    }

    default HoneyWelderInputResult handleInput(final Player player, final HoneyWelderInputIntent intent) {
        return HoneyWelderInputResult.PASS;
    }

    default void renderPreview(final Player player) {
    }

    default Component hudText(final Player player) {
        return Component.empty();
    }
}
