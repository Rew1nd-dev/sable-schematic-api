package dev.rew1nd.sableschematicapi.tool.client;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class BlueprintLibraryToolMode implements BlueprintToolMode {
    @Override
    public ResourceLocation id() {
        return BlueprintToolModes.id("blueprint");
    }

    @Override
    public Component label() {
        return Component.translatable("sable_schematic_api.blueprint_tool.mode.blueprint");
    }

    @Override
    public UIElement createPage(final Player player) {
        return BlueprintToolUiRenderer.blueprintPage(player);
    }

    @Override
    public void renderPreview(final Player player) {
        BlueprintToolSelectionPreviewRenderers.render(player);
    }

    @Override
    public BlueprintToolInputResult handleInput(final BlueprintToolInputContext context) {
        return switch (context.intent()) {
            case ATTACK -> {
                BlueprintToolClientSession.advanceSelection(context.player());
                yield BlueprintToolInputResult.consume(true);
            }
            case SHIFT_ATTACK -> {
                BlueprintToolClientSession.clearSelection(context.player());
                yield BlueprintToolInputResult.consume(true);
            }
            case USE, SHIFT_USE -> {
                if (BlueprintToolClientSession.selectedBlueprint() != null) {
                    BlueprintToolClientSession.requestLoadAtLookTarget(context.player());
                } else {
                    BlueprintToolClientSession.setStatusKey("no_file_selected");
                    BlueprintToolClientSession.notifyStatus(context.player(), ChatFormatting.YELLOW);
                }
                yield BlueprintToolInputResult.consume(true);
            }
        };
    }
}
