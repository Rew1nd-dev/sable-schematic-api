package dev.rew1nd.sableschematicapi.tool.ui;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.rew1nd.sableschematicapi.tool.client.BlueprintToolClientSession;
import dev.rew1nd.sableschematicapi.tool.client.BlueprintToolLocalFiles;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public final class BlueprintToolUiRenderer {
    private static final String KEY_PREFIX = "sable_schematic_api.blueprint_tool.";
    private static final float ROOT_WIDTH = 300;
    private static final float ROOT_HEIGHT = 220;
    private static final float PANEL_WIDTH = 270;
    private static final float ROW_HEIGHT = 16;
    private static final float LIST_HEIGHT = 122;
    private static final float BUTTON_WIDTH = 70;
    private static final float GAP = 4;

    private BlueprintToolUiRenderer() {
    }

    public static ModularUI renderClient(final Player player) {
        final UIElement root = new UIElement()
                .setId("sable_blueprint_tool_root")
                .layout(layout -> {
                    layout.width(ROOT_WIDTH);
                    layout.height(ROOT_HEIGHT);
                    layout.paddingAll(8);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.alignItems(AlignItems.CENTER);
                    layout.gapAll(GAP);
                })
                .style(style -> style.backgroundTexture(panelTexture()));

        final UIElement dialog = saveDialog(player);
        final Button save = textButton(tr("ui.save"));
        save.layout(layout -> {
            layout.width(BUTTON_WIDTH);
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

        final Button load = textButton(tr("ui.load"));
        load.layout(layout -> {
            layout.width(BUTTON_WIDTH);
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
                    layout.width(PANEL_WIDTH);
                    layout.height(ROW_HEIGHT);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.gapAll(GAP);
                })
                .addChildren(save, load);

        root.addChildren(
                toolbar,
                boundLabel(BlueprintToolClientSession::selectionLabel),
                localBlueprintList(player),
                boundLabel(BlueprintToolClientSession::status),
                dialog
        );
        dialog.setVisible(false);
        dialog.setDisplay(false);

        return ModularUI.of(
                UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC))),
                player
        );
    }

    private static ScrollerView localBlueprintList(final Player player) {
        final ScrollerView scroller = new ScrollerView();
        scroller.setId("sable_blueprint_local_list");
        scroller.layout(layout -> {
            layout.width(PANEL_WIDTH);
            layout.height(LIST_HEIGHT);
        });
        scroller.style(style -> style.backgroundTexture(displayTexture()));
        scroller.viewContainer(container -> container.layout(layout -> {
            layout.width(PANEL_WIDTH - 10);
            layout.heightAuto();
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(2);
        }));

        final List<BlueprintToolLocalFiles.Entry> entries = BlueprintToolClientSession.localBlueprints();
        if (entries.isEmpty()) {
            scroller.addScrollViewChild(emptyRow());
            return scroller;
        }

        for (final BlueprintToolLocalFiles.Entry entry : entries) {
            scroller.addScrollViewChild(fileRow(player, entry));
        }
        return scroller;
    }

    private static UIElement fileRow(final Player player, final BlueprintToolLocalFiles.Entry entry) {
        final Button row = textButton(Component.literal(entry.name()));
        row.layout(layout -> {
            layout.width(PANEL_WIDTH - 12);
            layout.height(ROW_HEIGHT);
        });
        row.setOnClick(event -> {
            BlueprintToolClientSession.selectBlueprint(entry);
            BlueprintToolClientSession.notifyStatus(player, ChatFormatting.AQUA);
        });
        return row;
    }

    private static UIElement emptyRow() {
        final Label label = bareLabel(Horizontal.LEFT);
        label.setText(tr("ui.no_local_schematics"));
        label.layout(layout -> {
            layout.width(PANEL_WIDTH - 12);
            layout.height(ROW_HEIGHT);
        });
        return label;
    }

    private static UIElement saveDialog(final Player player) {
        final UIElement dialog = new UIElement()
                .setId("sable_blueprint_save_dialog")
                .layout(layout -> {
                    layout.positionType(TaffyPosition.ABSOLUTE);
                    layout.left(18);
                    layout.top(62);
                    layout.width(PANEL_WIDTH);
                    layout.height(82);
                    layout.paddingAll(8);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(GAP);
                })
                .style(style -> style.backgroundTexture(dialogTexture()));

        final Label title = bareLabel(Horizontal.LEFT);
        title.setText(tr("ui.save_as"));
        title.layout(layout -> {
            layout.width(PANEL_WIDTH - 16);
            layout.height(ROW_HEIGHT);
        });

        final TextField name = textField("sable_blueprint_save_name", PANEL_WIDTH - 16);
        name.setText(tr("ui.default_name").getString());

        final Button confirm = textButton(tr("ui.confirm"));
        confirm.layout(layout -> {
            layout.width(BUTTON_WIDTH);
            layout.height(ROW_HEIGHT);
        });
        confirm.setOnClick(event -> {
            BlueprintToolClientSession.requestSave(name.getText());
            dialog.setVisible(false);
            dialog.setDisplay(false);
        });

        final Button cancel = textButton(tr("ui.cancel"));
        cancel.layout(layout -> {
            layout.width(BUTTON_WIDTH);
            layout.height(ROW_HEIGHT);
        });
        cancel.setOnClick(event -> {
            dialog.setVisible(false);
            dialog.setDisplay(false);
        });

        final UIElement buttons = new UIElement()
                .layout(layout -> {
                    layout.width(PANEL_WIDTH - 16);
                    layout.height(ROW_HEIGHT);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.gapAll(GAP);
                })
                .addChildren(confirm, cancel);

        return dialog.addChildren(title, name, buttons);
    }

    private static Label boundLabel(final java.util.function.Supplier<Component> supplier) {
        final Label label = bareLabel(Horizontal.LEFT);
        label.bindDataSource(SupplierDataSource.<Component>of(supplier).frequency(5));
        label.layout(layout -> {
            layout.width(PANEL_WIDTH);
            layout.height(ROW_HEIGHT);
        });
        label.style(style -> style
                .backgroundTexture(displayTexture())
                .overflowVisible(false));
        return label;
    }

    private static Button textButton(final Component text) {
        final Button button = new Button();
        button.setText(text);
        button.buttonStyle(style -> style
                .baseTexture(buttonTexture(0xFFE7ECF3, 0xFF657089))
                .hoverTexture(buttonTexture(0xFFF3F6FA, 0xFF7A879F))
                .pressedTexture(buttonTexture(0xFFD8E0EA, 0xFF566178)));
        button.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textColor(0xFF253041)
                .textShadow(false)
                .fontSize(8)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
        return button;
    }

    private static TextField textField(final String id, final float width) {
        final TextField field = new TextField();
        field.setId(id);
        field.style(style -> style.backgroundTexture(fieldTexture()));
        field.textFieldStyle(style -> style
                .textColor(0xFF1F2937)
                .cursorColor(0xFF1F2937)
                .textShadow(false)
                .fontSize(8));
        field.layout(layout -> {
            layout.width(width);
            layout.height(ROW_HEIGHT);
            layout.paddingHorizontal(2);
            layout.paddingVertical(1);
        });
        return field;
    }

    private static Label bareLabel(final Horizontal align) {
        final Label label = new Label();
        label.setText(Component.empty());
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textWrap(TextWrap.HOVER_ROLL)
                .rollSpeed(1.0F)
                .textColor(0xFF253041)
                .textShadow(false)
                .fontSize(8)
                .textAlignHorizontal(align)
                .textAlignVertical(Vertical.CENTER));
        label.style(style -> style.overflowVisible(false));
        return label;
    }

    private static IGuiTexture panelTexture() {
        return SDFRectTexture.of(0xFFF1F4F8)
                .setBorderColor(0xFF66718D)
                .setRadius(2)
                .setStroke(1);
    }

    private static IGuiTexture dialogTexture() {
        return SDFRectTexture.of(0xFFF8FAFC)
                .setBorderColor(0xFF4F5B73)
                .setRadius(2)
                .setStroke(1);
    }

    private static IGuiTexture fieldTexture() {
        return SDFRectTexture.of(0xFFFFFFFF)
                .setBorderColor(0xFF748097)
                .setRadius(2)
                .setStroke(1);
    }

    private static IGuiTexture displayTexture() {
        return SDFRectTexture.of(0xFFE7ECF3)
                .setBorderColor(0xFF748097)
                .setRadius(2)
                .setStroke(1);
    }

    private static IGuiTexture buttonTexture(final int fill, final int border) {
        return SDFRectTexture.of(fill)
                .setBorderColor(border)
                .setRadius(2)
                .setStroke(1);
    }

    private static Component tr(final String key, final Object... args) {
        return Component.translatable(KEY_PREFIX + key, args);
    }
}
