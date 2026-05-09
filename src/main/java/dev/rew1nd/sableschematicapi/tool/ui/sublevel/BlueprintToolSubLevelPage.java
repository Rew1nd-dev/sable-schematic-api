package dev.rew1nd.sableschematicapi.tool.ui.sublevel;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import dev.rew1nd.sableschematicapi.tool.client.mode.BlueprintToolModes;
import dev.rew1nd.sableschematicapi.tool.client.session.BlueprintToolClientSession;
import dev.rew1nd.sableschematicapi.tool.client.sublevel.BlueprintToolSubLevelSortMode;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.world.entity.player.Player;

import java.util.List;

import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.boundLabel;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.iconButton;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.modeHeader;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.tr;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_HEIGHT;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.GAP;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ICON_BUTTON_SIZE;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ROW_HEIGHT;

public final class BlueprintToolSubLevelPage {
    private BlueprintToolSubLevelPage() {
    }

    public static UIElement create(final Player player) {
        final UIElement page = new UIElement()
                .setId("sable_blueprint_tool_sublevel_page")
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

        final Button refresh = iconButton(Icons.REPLAY, tr("ui.refresh"));
        refresh.layout(layout -> {
            layout.width(ICON_BUTTON_SIZE);
            layout.height(ROW_HEIGHT);
        });
        refresh.setOnClick(event -> BlueprintToolClientSession.requestSubLevelRefresh());

        final Selector<BlueprintToolSubLevelSortMode> sort = new Selector<>();
        sort.setCandidates(List.of(BlueprintToolSubLevelSortMode.values()));
        sort.setCandidateUIProvider(UIElementProvider.text(BlueprintToolSubLevelRows::subLevelSortText));
        sort.setSelected(BlueprintToolClientSession.subLevelSortMode());
        sort.setOnValueChanged(BlueprintToolClientSession::setSubLevelSortMode);
        sort.layout(layout -> {
            layout.width(CONTENT_WIDTH - ICON_BUTTON_SIZE - GAP);
            layout.height(ROW_HEIGHT);
        });

        final UIElement toolbar = new UIElement()
                .layout(layout -> {
                    layout.width(CONTENT_WIDTH);
                    layout.height(ROW_HEIGHT);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.gapAll(GAP);
                })
                .addChildren(refresh, sort);

        final BlueprintToolSubLevelDetailDialog detailDialog = new BlueprintToolSubLevelDetailDialog();
        page.addChildren(
                modeHeader(BlueprintToolModes.SUBLEVELS.label()),
                toolbar,
                BlueprintToolSubLevelList.create(player),
                boundLabel(BlueprintToolClientSession::status),
                detailDialog
        );
        return page;
    }
}
