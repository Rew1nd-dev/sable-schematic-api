package dev.rew1nd.sableschematicapi.tool.ui.blueprint;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import dev.rew1nd.sableschematicapi.tool.client.session.BlueprintToolClientSession;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;

import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.bareLabel;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.dialogTexture;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.iconButton;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.textField;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.tr;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.GAP;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ICON_BUTTON_SIZE;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ROW_HEIGHT;

final class BlueprintToolSaveDialog {
    private BlueprintToolSaveDialog() {
    }

    static UIElement create() {
        final UIElement dialog = new UIElement()
                .setId("sable_blueprint_save_dialog")
                .layout(layout -> {
                    layout.positionType(TaffyPosition.ABSOLUTE);
                    layout.left(0);
                    layout.top(64);
                    layout.width(CONTENT_WIDTH);
                    layout.height(82);
                    layout.paddingAll(8);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(GAP);
                })
                .style(style -> style.backgroundTexture(dialogTexture()));

        final Label title = bareLabel(Horizontal.LEFT);
        title.setText(tr("ui.save_as"));
        title.layout(layout -> {
            layout.width(CONTENT_WIDTH - 16);
            layout.height(ROW_HEIGHT);
        });

        final TextField name = textField("sable_blueprint_save_name", CONTENT_WIDTH - 16);
        name.setText(tr("ui.default_name").getString());

        final Button confirm = iconButton(Icons.CHECK, tr("ui.confirm"));
        confirm.layout(layout -> {
            layout.width(ICON_BUTTON_SIZE);
            layout.height(ROW_HEIGHT);
        });
        confirm.setOnClick(event -> {
            BlueprintToolClientSession.requestSave(name.getText());
            dialog.setVisible(false);
            dialog.setDisplay(false);
        });

        final Button cancel = iconButton(Icons.CLOSE, tr("ui.cancel"));
        cancel.layout(layout -> {
            layout.width(ICON_BUTTON_SIZE);
            layout.height(ROW_HEIGHT);
        });
        cancel.setOnClick(event -> {
            dialog.setVisible(false);
            dialog.setDisplay(false);
        });

        final UIElement buttons = new UIElement()
                .layout(layout -> {
                    layout.width(CONTENT_WIDTH - 16);
                    layout.height(ROW_HEIGHT);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.gapAll(GAP);
                })
                .addChildren(confirm, cancel);

        return dialog.addChildren(title, name, buttons);
    }
}
