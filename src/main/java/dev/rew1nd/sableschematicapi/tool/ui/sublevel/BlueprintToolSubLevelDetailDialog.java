package dev.rew1nd.sableschematicapi.tool.ui.sublevel;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import dev.rew1nd.sableschematicapi.tool.client.session.BlueprintToolClientSession;
import dev.rew1nd.sableschematicapi.tool.client.sublevel.BlueprintToolSubLevelEntry;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.UUID;

import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.bareLabel;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.dialogTexture;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.displayTexture;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.fieldTexture;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.iconButton;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.textField;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.tr;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_HEIGHT;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.CONTENT_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.GAP;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ICON_BUTTON_SIZE;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.ROW_HEIGHT;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.SUBLEVEL_DETAIL_WIDTH;
import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiLayout.SUBLEVEL_INFO_LABEL_WIDTH;

final class BlueprintToolSubLevelDetailDialog extends UIElement {
    private UUID renderedUuid;
    private int renderedRevision = Integer.MIN_VALUE;

    BlueprintToolSubLevelDetailDialog() {
        setId("sable_blueprint_sublevel_detail");
        layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(20);
            layout.width(CONTENT_WIDTH);
            layout.height(CONTENT_HEIGHT - 20);
            layout.paddingAll(8);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(GAP);
        });
        style(style -> style.backgroundTexture(dialogTexture()));
        setVisible(false);
        setDisplay(true);
    }

    @Override
    public void screenTick() {
        super.screenTick();
        sync();
    }

    private void sync() {
        final BlueprintToolSubLevelEntry entry = BlueprintToolClientSession.openedSubLevelDetail();
        if (entry == null) {
            setVisible(false);
            this.renderedUuid = null;
            this.renderedRevision = Integer.MIN_VALUE;
            return;
        }

        setVisible(true);
        final int currentRevision = BlueprintToolClientSession.subLevelRevision();
        if (entry.uuid().equals(this.renderedUuid) && currentRevision == this.renderedRevision) {
            return;
        }

        this.renderedUuid = entry.uuid();
        this.renderedRevision = currentRevision;
        rebuild(entry);
    }

    private void rebuild(final BlueprintToolSubLevelEntry entry) {
        clearAllChildren();

        final Button close = actionButton(Icons.CLOSE, tr("ui.close"));
        close.setOnClick(event -> {
            BlueprintToolClientSession.closeSubLevelDetail();
            sync();
        });

        final UIElement commandRow = new UIElement()
                .layout(layout -> {
                    layout.width(SUBLEVEL_DETAIL_WIDTH);
                    layout.heightAuto();
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.END);
                    layout.justifyContent(AlignContent.FLEX_END);
                })
                .addChild(close);

        addChildren(commandRow, infoColumn(entry), commandColumn(entry));
    }

    private UIElement infoColumn(final BlueprintToolSubLevelEntry entry) {
        return new UIElement()
                .layout(layout -> {
                    layout.width(SUBLEVEL_DETAIL_WIDTH);
                    layout.heightAuto();
                    layout.paddingAll(4);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(2);
                })
                .style(style -> style.backgroundTexture(displayTexture()))
                .addChildren(
                        infoRow(Component.literal("UUID"), Component.literal(entry.uuid().toString())),
                        infoRow(Component.literal("Name"), Component.literal(entry.displayName())),
                        infoRow(Component.literal("Pos"), Component.literal(positionText(entry) + " (" + entry.distanceLabel() + ")"))
                );
    }

    private UIElement infoRow(final Component key, final Component value) {
        final Label keyLabel = detailLabel(key);
        keyLabel.layout(layout -> {
            layout.width(SUBLEVEL_INFO_LABEL_WIDTH);
            layout.height(ROW_HEIGHT);
            layout.paddingHorizontal(2);
        });

        final Label valueLabel = detailLabel(value);
        valueLabel.layout(layout -> {
            layout.width(SUBLEVEL_DETAIL_WIDTH - 8 - SUBLEVEL_INFO_LABEL_WIDTH - GAP);
            layout.height(ROW_HEIGHT);
            layout.paddingHorizontal(2);
        });

        return new UIElement()
                .layout(layout -> {
                    layout.width(SUBLEVEL_DETAIL_WIDTH - 8);
                    layout.heightAuto();
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.STRETCH);
                    layout.gapAll(GAP);
                })
                .style(style -> style.backgroundTexture(fieldTexture()))
                .addChildren(keyLabel, valueLabel);
    }

    private UIElement commandColumn(final BlueprintToolSubLevelEntry entry) {
        final Button teleport = actionButton(Icons.TRANSFORM_TRANSLATE, tr("ui.sublevel_tp_player"));
        teleport.setOnClick(event -> BlueprintToolClientSession.requestTeleportPlayerToSubLevel(entry));

        final Button bring = actionButton(Icons.IMPORT, entry.groupSize() > 1
                ? tr("ui.sublevel_bring_group", entry.groupSize())
                : tr("ui.sublevel_bring"));
        bring.setOnClick(event -> BlueprintToolClientSession.requestBringSubLevel(entry));

        final Button toggleStatic = actionButton(Icons.LINK, entry.staticLocked()
                ? tr("ui.sublevel_switch_non_static")
                : tr("ui.sublevel_switch_static"));
        toggleStatic.setOnClick(event -> BlueprintToolClientSession.requestToggleStatic(entry));

        final UIElement buttonRow = new UIElement()
                .layout(layout -> {
                    layout.width(SUBLEVEL_DETAIL_WIDTH);
                    layout.heightAuto();
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.START);
                    layout.gapAll(3);
                    layout.justifyContent(AlignContent.FLEX_START);
                })
                .addChildren(teleport, bring, toggleStatic);

        final TextField rename = textField("sable_blueprint_sublevel_rename", SUBLEVEL_DETAIL_WIDTH - GAP - ICON_BUTTON_SIZE);
        rename.setText(entry.name() == null ? "" : entry.name());
        final Button renameOk = iconButton(Icons.CHECK, tr("ui.ok"));
        renameOk.layout(layout -> {
            layout.width(ICON_BUTTON_SIZE);
            layout.height(ROW_HEIGHT);
        });
        renameOk.setOnClick(event -> BlueprintToolClientSession.requestRenameSubLevel(entry, rename.getText()));

        final UIElement renameRow = new UIElement()
                .layout(layout -> {
                    layout.width(SUBLEVEL_DETAIL_WIDTH);
                    layout.heightAuto();
                    layout.flexDirection(FlexDirection.ROW);
                    layout.gapAll(GAP);
                })
                .addChildren(rename, renameOk);

        return new UIElement()
                .layout(layout -> {
                    layout.width(SUBLEVEL_DETAIL_WIDTH);
                    layout.heightAuto();
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(GAP);
                })
                .addChildren(buttonRow, renameRow);
    }

    private Button actionButton(final IGuiTexture icon, final Component tooltip) {
        final Button button = iconButton(icon, tooltip);
        button.layout(layout -> {
            layout.width(ICON_BUTTON_SIZE);
            layout.height(ROW_HEIGHT);
        });
        return button;
    }

    private Label detailLabel(final Component text) {
        final Label label = bareLabel(Horizontal.LEFT);
        label.setText(text);
        label.layout(layout -> {
            layout.width(CONTENT_WIDTH - 16);
            layout.height(ROW_HEIGHT);
        });
        return label;
    }

    private String positionText(final BlueprintToolSubLevelEntry entry) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", entry.x(), entry.y(), entry.z());
    }
}
