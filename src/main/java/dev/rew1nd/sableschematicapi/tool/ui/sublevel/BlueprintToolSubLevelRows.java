package dev.rew1nd.sableschematicapi.tool.ui.sublevel;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.rew1nd.sableschematicapi.tool.client.session.BlueprintToolClientSession;
import dev.rew1nd.sableschematicapi.tool.client.sublevel.BlueprintToolSubLevelEntry;
import dev.rew1nd.sableschematicapi.tool.client.sublevel.BlueprintToolSubLevelGroup;
import dev.rew1nd.sableschematicapi.tool.client.sublevel.BlueprintToolSubLevelSortMode;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.BORDER_COLOR;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.BUTTON_HOVER_COLOR;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.BUTTON_PRESSED_COLOR;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.TEXT_COLOR;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.bareLabel;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.buttonTexture;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.groupHeaderTexture;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.groupTexture;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.selectedRowTexture;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.textButton;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.tr;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.GROUP_INNER_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.GROUP_PADDING;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ROW_HEIGHT;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.SCROLL_ROW_WIDTH;

final class BlueprintToolSubLevelRows {
    private BlueprintToolSubLevelRows() {
    }

    static UIElement group(final Player player, final BlueprintToolSubLevelGroup group) {
        final UIElement column = new UIElement()
                .layout(layout -> {
                    layout.width(SCROLL_ROW_WIDTH);
                    layout.heightAuto();
                    layout.paddingAll(GROUP_PADDING);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(2);
                })
                .style(style -> style.backgroundTexture(groupTexture()));

        column.addChild(groupHeader(group));
        for (final BlueprintToolSubLevelEntry entry : group.members()) {
            column.addChild(row(player, entry));
        }
        return column;
    }

    static UIElement emptyRow() {
        final Label label = bareLabel(Horizontal.LEFT);
        label.setText(tr("ui.no_sublevels"));
        label.layout(layout -> {
            layout.width(SCROLL_ROW_WIDTH);
            layout.height(ROW_HEIGHT);
        });
        return label;
    }

    static Component subLevelSortText(final BlueprintToolSubLevelSortMode mode) {
        final BlueprintToolSubLevelSortMode safeMode = mode == null ? BlueprintToolSubLevelSortMode.NAME_DESC : mode;
        return tr(safeMode.translationKey());
    }

    private static UIElement row(final Player player, final BlueprintToolSubLevelEntry entry) {
        final boolean selected = entry.uuid().equals(BlueprintToolClientSession.selectedSubLevel());
        final Button row = textButton(rowText(entry));
        row.layout(layout -> {
            layout.width(GROUP_INNER_WIDTH);
            layout.height(ROW_HEIGHT);
        });
        if (selected) {
            row.buttonStyle(style -> style
                    .baseTexture(selectedRowTexture())
                    .hoverTexture(buttonTexture(BUTTON_HOVER_COLOR, BORDER_COLOR))
                    .pressedTexture(buttonTexture(BUTTON_PRESSED_COLOR, BORDER_COLOR)));
            row.textStyle(style -> style.textColor(TEXT_COLOR));
        }
        row.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 1) {
                BlueprintToolClientSession.openSubLevelDetail(entry);
                event.stopPropagation();
                return;
            }

            if (event.button == 0) {
                BlueprintToolClientSession.selectSubLevel(entry);
                BlueprintToolClientSession.notifyStatus(player, ChatFormatting.AQUA);
                event.stopPropagation();
            }
        });
        return row;
    }

    private static UIElement groupHeader(final BlueprintToolSubLevelGroup group) {
        final Label label = bareLabel(Horizontal.LEFT);
        label.setText(tr("ui.sublevel_group_header", group.members().size(), group.distanceLabel(), group.representativeId()));
        label.layout(layout -> {
            layout.width(GROUP_INNER_WIDTH);
            layout.height(ROW_HEIGHT);
        });
        label.style(style -> style
                .backgroundTexture(groupHeaderTexture())
                .overflowVisible(false));
        return label;
    }

    private static Component rowText(final BlueprintToolSubLevelEntry entry) {
        final String staticMarker = entry.staticLocked() ? " static" : "";
        return Component.literal("%s [%s%s] %s".formatted(
                entry.displayName(),
                entry.stateLabel(),
                staticMarker,
                entry.dimension()
        ));
    }
}
