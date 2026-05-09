package dev.rew1nd.sableschematicapi.tool.ui.delete;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.rew1nd.sableschematicapi.tool.client.mode.BlueprintToolModes;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.world.entity.player.Player;

import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.modeHeader;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_HEIGHT;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.GAP;

public final class BlueprintToolDeletePage {
    private BlueprintToolDeletePage() {
    }

    public static UIElement create(final Player player) {
        final UIElement page = new UIElement()
                .setId("sable_blueprint_tool_delete_page")
                .layout(layout -> {
                    layout.positionType(TaffyPosition.ABSOLUTE);
                    layout.left(0);
                    layout.top(0);
                    layout.width(CONTENT_WIDTH);
                    layout.height(CONTENT_HEIGHT);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.alignItems(AlignItems.CENTER);
                    layout.gapAll(GAP);
                });

        page.addChild(modeHeader(BlueprintToolModes.DELETE.label()));
        return page;
    }
}
