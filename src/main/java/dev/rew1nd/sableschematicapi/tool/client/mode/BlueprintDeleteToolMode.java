package dev.rew1nd.sableschematicapi.tool.client.mode;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.rew1nd.sableschematicapi.tool.client.input.BlueprintToolInputContext;
import dev.rew1nd.sableschematicapi.tool.client.input.BlueprintToolInputResult;
import dev.rew1nd.sableschematicapi.tool.client.session.BlueprintToolClientSession;
import dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class BlueprintDeleteToolMode implements BlueprintToolMode {
    @Override
    public ResourceLocation id() {
        return BlueprintToolModes.id("delete");
    }

    @Override
    public Component label() {
        return Component.translatable("sable_schematic_api.blueprint_tool.mode.delete");
    }

    @Override
    public UIElement createPage(final Player player) {
        return BlueprintToolUiRenderer.deletePage(player);
    }

    @Override
    public BlueprintToolInputResult handleInput(final BlueprintToolInputContext context) {
        return switch (context.intent()) {
            case ATTACK, SHIFT_ATTACK -> {
                BlueprintToolClientSession.requestDeleteLookedSubLevel(context.player());
                yield BlueprintToolInputResult.consume(true);
            }
            case USE, SHIFT_USE -> {
                BlueprintToolClientSession.setStatusKey("delete_ready");
                BlueprintToolClientSession.notifyStatus(context.player(), ChatFormatting.YELLOW);
                yield BlueprintToolInputResult.consume(true);
            }
        };
    }
}
