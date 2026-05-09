package dev.rew1nd.sableschematicapi.tool.ui.sublevel;

import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import dev.rew1nd.sableschematicapi.tool.client.session.BlueprintToolClientSession;
import dev.rew1nd.sableschematicapi.tool.client.sublevel.BlueprintToolSubLevelGroup;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.world.entity.player.Player;

import java.util.List;

import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.displayTexture;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.SUBLEVEL_LIST_HEIGHT;

final class BlueprintToolSubLevelList extends ScrollerView {
    private final Player player;
    private int revision = Integer.MIN_VALUE;

    private BlueprintToolSubLevelList(final Player player) {
        this.player = player;
        refreshIfNeeded();
    }

    static ScrollerView create(final Player player) {
        final ScrollerView scroller = new BlueprintToolSubLevelList(player);
        scroller.setId("sable_blueprint_sublevel_list");
        scroller.layout(layout -> {
            layout.width(CONTENT_WIDTH);
            layout.height(SUBLEVEL_LIST_HEIGHT);
        });
        scroller.style(style -> style.backgroundTexture(displayTexture()));
        scroller.viewContainer(container -> container.layout(layout -> {
            layout.width(CONTENT_WIDTH - 10);
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
        final int current = BlueprintToolClientSession.subLevelRevision();
        if (current == this.revision) {
            return;
        }

        this.revision = current;
        rebuild();
    }

    private void rebuild() {
        clearAllScrollViewChildren();
        final List<BlueprintToolSubLevelGroup> groups = BlueprintToolClientSession.subLevelGroups();
        if (groups.isEmpty()) {
            addScrollViewChild(BlueprintToolSubLevelRows.emptyRow());
            return;
        }

        for (final BlueprintToolSubLevelGroup group : groups) {
            addScrollViewChild(BlueprintToolSubLevelRows.group(this.player, group));
        }
    }
}
