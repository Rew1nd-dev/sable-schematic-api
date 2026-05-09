package dev.rew1nd.sableschematicapi.tool.ui;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.rew1nd.sableschematicapi.tool.client.mode.BlueprintToolMode;
import dev.rew1nd.sableschematicapi.tool.client.mode.BlueprintToolModes;
import dev.rew1nd.sableschematicapi.tool.client.session.BlueprintToolClientSession;
import dev.rew1nd.sableschematicapi.tool.ui.blueprint.BlueprintToolBlueprintPage;
import dev.rew1nd.sableschematicapi.tool.ui.delete.BlueprintToolDeletePage;
import dev.rew1nd.sableschematicapi.tool.ui.sublevel.BlueprintToolSubLevelPage;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.world.entity.player.Player;

import java.util.List;

import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.iconButton;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.panelTexture;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_HEIGHT;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.GAP;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.MODE_BAR_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ROOT_HEIGHT;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ROOT_PADDING;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ROOT_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.TAB_HEIGHT;

public final class BlueprintToolUiRenderer {
    private BlueprintToolUiRenderer() {
    }

    public static ModularUI renderClient(final Player player) {
        final UIElement root = new UIElement()
                .setId("sable_blueprint_tool_root")
                .layout(layout -> {
                    layout.width(ROOT_WIDTH);
                    layout.height(ROOT_HEIGHT);
                    layout.paddingAll(ROOT_PADDING);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.gapAll(GAP);
                })
                .style(style -> style.backgroundTexture(panelTexture()));

        final UIElement tabs = new UIElement()
                .setId("sable_blueprint_tool_tabs")
                .layout(layout -> {
                    layout.width(MODE_BAR_WIDTH);
                    layout.height(CONTENT_HEIGHT);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(GAP);
                });
        final UIElement content = new UIElement()
                .setId("sable_blueprint_tool_content")
                .layout(layout -> {
                    layout.positionType(TaffyPosition.RELATIVE);
                    layout.width(CONTENT_WIDTH);
                    layout.height(CONTENT_HEIGHT);
                });
        final List<BlueprintToolMode> modes = BlueprintToolModes.all();
        final List<UIElement> pages = new java.util.ArrayList<>(modes.size());
        final List<Button> tabButtons = new java.util.ArrayList<>(modes.size());

        for (final BlueprintToolMode mode : modes) {
            final Button tab = iconButton(mode.icon(), mode.label());
            tab.layout(layout -> {
                layout.width(MODE_BAR_WIDTH);
                layout.height(TAB_HEIGHT);
            });
            final UIElement page = mode.createPage(player);
            tab.setOnClick(event -> {
                BlueprintToolClientSession.setMode(mode);
                selectMode(mode, modes, pages, tabButtons);
            });
            tabs.addChild(tab);
            pages.add(page);
            tabButtons.add(tab);
        }

        root.addChild(tabs);
        for (final UIElement page : pages) {
            content.addChild(page);
        }
        root.addChild(content);
        selectMode(BlueprintToolClientSession.currentMode(), modes, pages, tabButtons);

        return ModularUI.of(
                UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC))),
                player
        );
    }

    public static UIElement blueprintPage(final Player player) {
        return BlueprintToolBlueprintPage.create(player);
    }

    public static UIElement deletePage(final Player player) {
        return BlueprintToolDeletePage.create(player);
    }

    public static UIElement subLevelPage(final Player player) {
        return BlueprintToolSubLevelPage.create(player);
    }

    private static void selectMode(final BlueprintToolMode selected,
                                   final List<BlueprintToolMode> modes,
                                   final List<UIElement> pages,
                                   final List<Button> tabButtons) {
        for (int i = 0; i < modes.size(); i++) {
            final boolean active = modes.get(i).id().equals(selected.id());
            pages.get(i).setVisible(active);
            pages.get(i).setDisplay(active);
            tabButtons.get(i).setActive(!active);
        }
    }
}
