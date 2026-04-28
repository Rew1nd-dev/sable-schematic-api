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
import dev.rew1nd.sableschematicapi.tool.client.BlueprintToolMode;
import dev.rew1nd.sableschematicapi.tool.client.BlueprintToolClientSession;
import dev.rew1nd.sableschematicapi.tool.client.BlueprintToolModes;
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
    private static final float ROOT_PADDING = 8;
    private static final float GAP = 4;
    private static final float MODE_BAR_WIDTH = 56;
    private static final float CONTENT_WIDTH = 280;
    private static final float CONTENT_HEIGHT = 224;
    private static final float ROOT_WIDTH = MODE_BAR_WIDTH + CONTENT_WIDTH + GAP + ROOT_PADDING * 2;
    private static final float ROOT_HEIGHT = CONTENT_HEIGHT + ROOT_PADDING * 2;
    private static final float ROW_HEIGHT = 16;
    private static final float LIST_HEIGHT = 140;
    private static final float BUTTON_WIDTH = 70;
    private static final float TAB_HEIGHT = 22;

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
            final Button tab = textButton(mode.label());
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
                    layout.width(CONTENT_WIDTH);
                    layout.height(ROW_HEIGHT);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.gapAll(GAP);
                })
                .addChildren(save, load);

        page.addChildren(
                modeHeader(BlueprintToolModes.BLUEPRINT.label()),
                toolbar,
                boundLabel(BlueprintToolClientSession::selectionLabel),
                localBlueprintList(player),
                boundLabel(BlueprintToolClientSession::status),
                dialog
        );
        dialog.setVisible(false);
        dialog.setDisplay(false);

        return page;
    }

    public static UIElement deletePage(final Player player) {
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

    private static ScrollerView localBlueprintList(final Player player) {
        final ScrollerView scroller = new RefreshingBlueprintList(player);
        scroller.setId("sable_blueprint_local_list");
        scroller.layout(layout -> {
            layout.width(CONTENT_WIDTH);
            layout.height(LIST_HEIGHT);
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

    private static UIElement fileRow(final Player player, final BlueprintToolLocalFiles.Entry entry) {
        final Button row = textButton(Component.literal(entry.name()));
        row.layout(layout -> {
            layout.width(CONTENT_WIDTH - 12);
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
            layout.width(CONTENT_WIDTH - 12);
            layout.height(ROW_HEIGHT);
        });
        return label;
    }

    private static UIElement saveDialog(final Player player) {
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
                    layout.width(CONTENT_WIDTH - 16);
                    layout.height(ROW_HEIGHT);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.gapAll(GAP);
                })
                .addChildren(confirm, cancel);

        return dialog.addChildren(title, name, buttons);
    }

    private static Label modeHeader(final Component modeLabel) {
        final Label label = bareLabel(Horizontal.LEFT);
        label.setText(modeLabel);
        label.layout(layout -> {
            layout.width(CONTENT_WIDTH);
            layout.height(ROW_HEIGHT);
        });
        return label;
    }

    private static Label boundLabel(final java.util.function.Supplier<Component> supplier) {
        final Label label = bareLabel(Horizontal.LEFT);
        label.bindDataSource(SupplierDataSource.<Component>of(supplier).frequency(5));
        label.layout(layout -> {
            layout.width(CONTENT_WIDTH);
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
        button.setOverflowVisible(false);
        button.text.layout(layout -> layout.widthStretch());
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
                .textWrap(TextWrap.HOVER_ROLL)
                .rollSpeed(1.0F)
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

    private static final class RefreshingBlueprintList extends ScrollerView {
        private final Player player;
        private int revision = Integer.MIN_VALUE;

        private RefreshingBlueprintList(final Player player) {
            this.player = player;
            refreshIfNeeded();
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
                addScrollViewChild(emptyRow());
                return;
            }

            for (final BlueprintToolLocalFiles.Entry entry : entries) {
                addScrollViewChild(fileRow(this.player, entry));
            }
        }
    }
}
