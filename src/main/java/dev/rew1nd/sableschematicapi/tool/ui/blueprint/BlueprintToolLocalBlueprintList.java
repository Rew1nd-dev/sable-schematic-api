package dev.rew1nd.sableschematicapi.tool.ui.blueprint;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.rew1nd.sableschematicapi.tool.client.session.BlueprintToolClientSession;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;

import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.bareLabel;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.displayTexture;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.textButton;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.tr;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ROW_HEIGHT;

final class BlueprintToolLocalBlueprintList extends ScrollerView {
    private final Player player;
    private final float rowWidth;
    private int revision = Integer.MIN_VALUE;

    private BlueprintToolLocalBlueprintList(final Player player, final float width) {
        this.player = player;
        this.rowWidth = width - 12;
        refreshIfNeeded();
    }

    static ScrollerView create(final Player player, final float width, final float height) {
        final ScrollerView scroller = new BlueprintToolLocalBlueprintList(player, width);
        scroller.setId("sable_blueprint_local_list");
        scroller.layout(layout -> {
            layout.width(width);
            layout.height(height);
        });
        scroller.style(style -> style.backgroundTexture(displayTexture()));
        scroller.viewContainer(container -> container.layout(layout -> {
            layout.width(width - 10);
            layout.heightAuto();
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(2);
        }));

        return scroller;
    }

    @Override
    public void screenTick() {
        super.screenTick();
        refreshIfNeeded();
    }

    private void refreshIfNeeded() {
        final int current = BlueprintToolClientSession.localBlueprintRevision();
        if (current == this.revision) {
            return;
        }

        this.revision = current;
        rebuild();
    }

    private void rebuild() {
        clearAllScrollViewChildren();
        final List<BlueprintToolLocalFiles.Entry> entries = BlueprintToolClientSession.localBlueprints();
        if (entries.isEmpty()) {
            addScrollViewChild(emptyRow(this.rowWidth));
            return;
        }

        for (final BlueprintToolLocalFiles.Entry entry : entries) {
            addScrollViewChild(fileRow(this.player, entry, this.rowWidth));
        }
    }

    private static UIElement fileRow(final Player player,
                                     final BlueprintToolLocalFiles.Entry entry,
                                     final float width) {
        final Button row = textButton(Component.literal(entry.name()));
        row.layout(layout -> {
            layout.width(width);
            layout.height(ROW_HEIGHT);
        });
        row.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 1) {
                BlueprintToolClientSession.openBlueprintPreview(entry);
                BlueprintToolClientSession.notifyStatus(player, ChatFormatting.AQUA);
                event.stopPropagation();
                return;
            }

            if (event.button == 0) {
                BlueprintToolClientSession.selectBlueprint(entry);
                BlueprintToolClientSession.notifyStatus(player, ChatFormatting.AQUA);
                event.stopPropagation();
            }
        });
        return row;
    }

    private static UIElement emptyRow(final float width) {
        final Label label = bareLabel(Horizontal.LEFT);
        label.setText(tr("ui.no_local_schematics"));
        label.layout(layout -> {
            layout.width(width);
            layout.height(ROW_HEIGHT);
        });
        return label;
    }
}
