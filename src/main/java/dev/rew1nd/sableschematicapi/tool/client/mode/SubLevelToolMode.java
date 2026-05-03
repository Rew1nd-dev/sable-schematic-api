package dev.rew1nd.sableschematicapi.tool.client.mode;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class SubLevelToolMode implements BlueprintToolMode {
    @Override
    public ResourceLocation id() {
        return BlueprintToolModes.id("sublevels");
    }

    @Override
    public Component label() {
        return Component.translatable("sable_schematic_api.blueprint_tool.mode.sublevels");
    }

    @Override
    public UIElement createPage(final Player player) {
        return BlueprintToolUiRenderer.subLevelPage(player);
    }
}
