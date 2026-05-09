package dev.rew1nd.sableschematicapi.tool.ui.blueprint;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.rew1nd.sableschematicapi.tool.client.mode.BlueprintToolModes;
import dev.rew1nd.sableschematicapi.tool.client.session.BlueprintToolClientSession;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;

import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.boundLabel;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.iconButton;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.modeHeader;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.tr;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_HEIGHT;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.GAP;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ICON_BUTTON_SIZE;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.LIST_HEIGHT;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ROW_HEIGHT;

public final class BlueprintToolBlueprintPage {
    private BlueprintToolBlueprintPage() {
    }

    public static UIElement create(final Player player) {
        final UIElement page = new UIElement()
                .setId("sable_blueprint_tool_blueprint_page")
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

        final UIElement dialog = BlueprintToolSaveDialog.create();
        final Button save = iconButton(Icons.SAVE, tr("ui.save"));
        save.layout(layout -> {
            layout.width(ICON_BUTTON_SIZE);
            layout.height(ROW_HEIGHT);
        });
        save.setOnClick(event -> {
            if (!BlueprintToolClientSession.hasRange()) {
                BlueprintToolClientSession.setStatusKey("set_start_end_first");
                BlueprintToolClientSession.notifyStatus(player, ChatFormatting.RED);
                return;
            }
            dialog.setVisible(true);
            dialog.setDisplay(true);
        });

        final Button load = iconButton(Icons.IMPORT, tr("ui.load"));
        load.layout(layout -> {
            layout.width(ICON_BUTTON_SIZE);
            layout.height(ROW_HEIGHT);
        });
        load.setOnClick(event -> {
            if (BlueprintToolClientSession.selectedBlueprint() == null) {
                BlueprintToolClientSession.setStatusKey("select_file");
                BlueprintToolClientSession.notifyStatus(player, ChatFormatting.YELLOW);
                return;
            }
            BlueprintToolClientSession.setStatusKey("ready_to_place");
            BlueprintToolClientSession.notifyStatus(player, ChatFormatting.GREEN);
            if (player.level().isClientSide()) {
                net.minecraft.client.Minecraft.getInstance().setScreen(null);
            }
        });

        final UIElement toolbar = new UIElement()
                .layout(layout -> {
                    layout.width(CONTENT_WIDTH);
                    layout.height(ROW_HEIGHT);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.gapAll(GAP);
                })
                .addChildren(save, load);

        final BlueprintToolMetadataDialog previewDialog = new BlueprintToolMetadataDialog();
        page.addChildren(
                modeHeader(BlueprintToolModes.BLUEPRINT.label()),
                toolbar,
                boundLabel(BlueprintToolClientSession::selectionLabel),
                BlueprintToolLocalBlueprintList.create(player, CONTENT_WIDTH, LIST_HEIGHT),
                boundLabel(BlueprintToolClientSession::status),
                dialog,
                previewDialog
        );
        dialog.setVisible(false);
        dialog.setDisplay(false);

        return page;
    }
}
