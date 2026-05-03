package dev.rew1nd.sableschematicapi.tool.client.mode;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.rew1nd.sableschematicapi.tool.client.input.BlueprintToolInputContext;
import dev.rew1nd.sableschematicapi.tool.client.input.BlueprintToolInputResult;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public interface BlueprintToolMode {
    ResourceLocation id();

    Component label();

    UIElement createPage(Player player);

    default void renderPreview(final Player player) {
    }

    default BlueprintToolInputResult handleInput(final BlueprintToolInputContext context) {
        return BlueprintToolInputResult.PASS;
    }
}
