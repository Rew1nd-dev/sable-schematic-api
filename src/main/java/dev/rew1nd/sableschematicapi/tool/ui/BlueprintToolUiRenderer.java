package dev.rew1nd.sableschematicapi.tool.ui;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.rew1nd.sableschematicapi.tool.client.mode.BlueprintToolMode;
import dev.rew1nd.sableschematicapi.tool.client.mode.BlueprintToolModes;
import dev.rew1nd.sableschematicapi.tool.client.preview.BlueprintToolLocalPreviewCache;
import dev.rew1nd.sableschematicapi.tool.client.session.BlueprintToolClientSession;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles;
import dev.rew1nd.sableschematicapi.tool.client.sublevel.BlueprintToolSubLevelEntry;
import dev.rew1nd.sableschematicapi.tool.client.sublevel.BlueprintToolSubLevelGroup;
import dev.rew1nd.sableschematicapi.tool.client.sublevel.BlueprintToolSubLevelSortMode;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BlueprintToolUiRenderer {
    private static final String KEY_PREFIX = "sable_schematic_api.blueprint_tool.";
    private static final float ROOT_PADDING = 8;
    private static final float GAP = 4;
    private static final float MODE_BAR_WIDTH = 56;
    private static final float CONTENT_WIDTH = 360;
    private static final float CONTENT_HEIGHT = 224;
    private static final float ROOT_WIDTH = MODE_BAR_WIDTH + CONTENT_WIDTH + GAP + ROOT_PADDING * 2;
    private static final float ROOT_HEIGHT = CONTENT_HEIGHT + ROOT_PADDING * 2;
    private static final float ROW_HEIGHT = 16;
    private static final float LIST_HEIGHT = 140;
    private static final float SUBLEVEL_LIST_HEIGHT = 144;
    private static final float BUTTON_WIDTH = 70;
    private static final float RENAME_BUTTON_WIDTH = 44;
    private static final float TAB_HEIGHT = 22;
    private static final float SCROLL_ROW_WIDTH = CONTENT_WIDTH - 12;
    private static final float GROUP_PADDING = 3;
    private static final float GROUP_INNER_WIDTH = SCROLL_ROW_WIDTH - GROUP_PADDING * 2;
    private static final float PREVIEW_DIALOG_INNER_WIDTH = CONTENT_WIDTH - 16;
    private static final float PREVIEW_DIALOG_SCROLL_HEIGHT = CONTENT_HEIGHT - 20 - 16 - ROW_HEIGHT - GAP;
    private static final float PREVIEW_CANVAS_HEIGHT = 128;
    private static final float DESCRIPTION_HEIGHT = 58;

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

        final BlueprintPreviewDialog previewDialog = new BlueprintPreviewDialog();
        page.addChildren(
                modeHeader(BlueprintToolModes.BLUEPRINT.label()),
                toolbar,
                boundLabel(BlueprintToolClientSession::selectionLabel),
                localBlueprintList(player, CONTENT_WIDTH, LIST_HEIGHT),
                boundLabel(BlueprintToolClientSession::status),
                dialog,
                previewDialog
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

    public static UIElement subLevelPage(final Player player) {
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

        final Button refresh = textButton(tr("ui.refresh"));
        refresh.layout(layout -> {
            layout.width(BUTTON_WIDTH);
            layout.height(ROW_HEIGHT);
        });
        refresh.setOnClick(event -> BlueprintToolClientSession.requestSubLevelRefresh());

        final Selector<BlueprintToolSubLevelSortMode> sort = new Selector<>();
        sort.setCandidates(List.of(BlueprintToolSubLevelSortMode.values()));
        sort.setCandidateUIProvider(UIElementProvider.text(BlueprintToolUiRenderer::subLevelSortText));
        sort.setSelected(BlueprintToolClientSession.subLevelSortMode());
        sort.setOnValueChanged(BlueprintToolClientSession::setSubLevelSortMode);
        sort.layout(layout -> {
            layout.width(CONTENT_WIDTH - BUTTON_WIDTH - GAP);
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

        final SubLevelDetailDialog detailDialog = new SubLevelDetailDialog();
        page.addChildren(
                modeHeader(BlueprintToolModes.SUBLEVELS.label()),
                toolbar,
                subLevelList(player),
                boundLabel(BlueprintToolClientSession::status),
                detailDialog
        );
        return page;
    }

    private static ScrollerView localBlueprintList(final Player player, final float width, final float height) {
        final ScrollerView scroller = new RefreshingBlueprintList(player, width);
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

    private static ScrollerView subLevelList(final Player player) {
        final ScrollerView scroller = new RefreshingSubLevelList(player);
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

    private static UIElement fileRow(final Player player, final BlueprintToolLocalFiles.Entry entry, final float width) {
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

    private static UIElement subLevelRow(final Player player, final BlueprintToolSubLevelEntry entry) {
        final boolean selected = entry.uuid().equals(BlueprintToolClientSession.selectedSubLevel());
        final Button row = textButton(subLevelRowText(entry));
        row.layout(layout -> {
            layout.width(GROUP_INNER_WIDTH);
            layout.height(ROW_HEIGHT);
        });
        if (selected) {
            row.buttonStyle(style -> style
                    .baseTexture(selectedRowTexture())
                    .hoverTexture(buttonTexture(0xFF748097, 0xFF374151))
                    .pressedTexture(buttonTexture(0xFF5D687D, 0xFF253041)));
            row.textStyle(style -> style.textColor(0xFFFFFFFF));
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

    private static UIElement subLevelGroup(final Player player, final BlueprintToolSubLevelGroup group) {
        final UIElement column = new UIElement()
                .layout(layout -> {
                    layout.width(SCROLL_ROW_WIDTH);
                    layout.heightAuto();
                    layout.paddingAll(GROUP_PADDING);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(2);
                })
                .style(style -> style.backgroundTexture(groupTexture()));

        column.addChild(subLevelGroupHeader(group));
        for (final BlueprintToolSubLevelEntry entry : group.members()) {
            column.addChild(subLevelRow(player, entry));
        }
        return column;
    }

    private static UIElement subLevelGroupHeader(final BlueprintToolSubLevelGroup group) {
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

    private static Component subLevelSortText(final BlueprintToolSubLevelSortMode mode) {
        final BlueprintToolSubLevelSortMode safeMode = mode == null ? BlueprintToolSubLevelSortMode.NAME_DESC : mode;
        return tr(safeMode.translationKey());
    }

    private static Component subLevelRowText(final BlueprintToolSubLevelEntry entry) {
        final String staticMarker = entry.staticLocked() ? " static" : "";
        return Component.literal("%s [%s%s] %s".formatted(
                entry.displayName(),
                entry.stateLabel(),
                staticMarker,
                entry.dimension()
        ));
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

    private static UIElement emptySubLevelRow() {
        final Label label = bareLabel(Horizontal.LEFT);
        label.setText(tr("ui.no_sublevels"));
        label.layout(layout -> {
            layout.width(SCROLL_ROW_WIDTH);
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

    private static TextArea textArea(final String id, final float width, final float height) {
        final TextArea area = new TextArea();
        area.setId(id);
        area.style(style -> style.backgroundTexture(fieldTexture()));
        area.contentView.style(style -> style.backgroundTexture(SDFRectTexture.of(0xFFFFFFFF)));
        area.textAreaStyle(style -> style
                .textColor(0xFF1F2937)
                .cursorColor(0xFF1F2937)
                .textShadow(false)
                .fontSize(8)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .viewMode(ScrollerMode.VERTICAL)
                .lineSpacing(1.0F));
        area.layout(layout -> {
            layout.width(width);
            layout.height(height);
            layout.paddingAll(2);
        });
        return area;
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

    private static IGuiTexture groupTexture() {
        return SDFRectTexture.of(0xFFEAF4EA)
                .setBorderColor(0xFF7C9B84)
                .setRadius(2)
                .setStroke(1);
    }

    private static IGuiTexture groupHeaderTexture() {
        return SDFRectTexture.of(0xFFD7E8D7)
                .setBorderColor(0xFF6F8F78)
                .setRadius(2)
                .setStroke(1);
    }

    private static IGuiTexture selectedRowTexture() {
        return buttonTexture(0xFF657089, 0xFF253041);
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

    private static void drawLargePreviewTile(final GUIContext guiContext,
                                             final SableBlueprintPreview preview,
                                             final SableBlueprintPreview.View view,
                                             final int x,
                                             final int y,
                                             final int size) {
        guiContext.graphics.fill(x, y, x + size, y + size, 0xFFEFF4FA);

        if (preview != null) {
            final int resolution = preview.resolution();
            final int[] pixels = preview.pixels(view);
            if (pixels != null && pixels.length == resolution * resolution) {
                for (int py = 0; py < size; py++) {
                    final int sourceY = py * resolution / size;
                    for (int px = 0; px < size; px++) {
                        final int sourceX = px * resolution / size;
                        final int color = pixels[sourceY * resolution + sourceX];
                        if ((color >>> 24) != 0) {
                            guiContext.graphics.fill(x + px, y + py, x + px + 1, y + py + 1, color);
                        }
                    }
                }
            }
        }

        drawBorder(guiContext, x, y, size, size, 0xFF748097);
        drawFullViewLabel(guiContext, view, x, y);
    }

    private static void drawFullViewLabel(final GUIContext guiContext,
                                          final SableBlueprintPreview.View view,
                                          final int x,
                                          final int y) {
        final Font font = Minecraft.getInstance().font;
        final String text = view.id().toUpperCase(Locale.ROOT);
        final int labelWidth = font.width(text) + 4;
        guiContext.graphics.fill(x + 1, y + 1, x + labelWidth, y + 10, 0xCCF8FAFC);
        guiContext.graphics.drawString(font, text, x + 3, y + 2, 0xFF253041, false);
    }

    private static void drawBorder(final GUIContext guiContext,
                                   final int x,
                                   final int y,
                                   final int width,
                                   final int height,
                                   final int color) {
        guiContext.graphics.fill(x, y, x + width, y + 1, color);
        guiContext.graphics.fill(x, y + height - 1, x + width, y + height, color);
        guiContext.graphics.fill(x, y, x + 1, y + height, color);
        guiContext.graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static final class BlueprintPreviewDialog extends UIElement {
        private BlueprintToolLocalFiles.Entry renderedEntry;
        private SableBlueprintPreview.View renderedView;
        private int renderedRevision = Integer.MIN_VALUE;

        private BlueprintPreviewDialog() {
            setId("sable_blueprint_preview_detail");
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
            final BlueprintToolLocalFiles.Entry entry = BlueprintToolClientSession.openedBlueprintPreview();
            if (entry == null) {
                setVisible(false);
                this.renderedEntry = null;
                this.renderedView = null;
                this.renderedRevision = Integer.MIN_VALUE;
                return;
            }

            setVisible(true);
            final int currentRevision = BlueprintToolClientSession.blueprintPreviewRevision();
            final SableBlueprintPreview.View view = BlueprintToolClientSession.openedBlueprintPreviewView();
            if (entry.equals(this.renderedEntry) && view == this.renderedView && currentRevision == this.renderedRevision) {
                return;
            }

            this.renderedEntry = entry;
            this.renderedView = view;
            this.renderedRevision = currentRevision;
            rebuild(entry, view);
        }

        private void rebuild(final BlueprintToolLocalFiles.Entry entry, final SableBlueprintPreview.View view) {
            clearAllChildren();

            addChildren(header(entry), metadataScroller(entry, view));
        }

        private UIElement header(final BlueprintToolLocalFiles.Entry entry) {
            final Label title = detailLabel(Component.literal(entry.name()));
            title.layout(layout -> {
                layout.widthStretch();
                layout.height(ROW_HEIGHT);
            });

            final Button close = textButton(Component.literal("X"));
            close.layout(layout -> {
                layout.width(20);
                layout.height(ROW_HEIGHT);
            });
            close.setOnClick(event -> {
                BlueprintToolClientSession.closeBlueprintPreview();
                sync();
            });

            return new UIElement()
                    .layout(layout -> {
                        layout.width(PREVIEW_DIALOG_INNER_WIDTH);
                        layout.height(ROW_HEIGHT);
                        layout.flexDirection(FlexDirection.ROW);
                        layout.alignItems(AlignItems.STRETCH);
                        layout.gapAll(GAP);
                    })
                    .addChildren(title, close);
        }

        private ScrollerView metadataScroller(final BlueprintToolLocalFiles.Entry entry,
                                              final SableBlueprintPreview.View view) {
            final ScrollerView scroller = new ScrollerView();
            scroller.layout(layout -> {
                layout.width(PREVIEW_DIALOG_INNER_WIDTH);
                layout.height(PREVIEW_DIALOG_SCROLL_HEIGHT);
            });
            scroller.style(style -> style.backgroundTexture(displayTexture()));
            scroller.viewContainer(container -> container.layout(layout -> {
                layout.width(PREVIEW_DIALOG_INNER_WIDTH - 12);
                layout.heightAuto();
                layout.flexDirection(FlexDirection.COLUMN);
                layout.gapAll(GAP);
                layout.paddingAll(4);
            }));

            scroller.addScrollViewChild(previewColumn(entry, view));
            scroller.addScrollViewChild(authorColumn());
            return scroller;
        }

        private UIElement previewColumn(final BlueprintToolLocalFiles.Entry entry,
                                        final SableBlueprintPreview.View view) {
            final Label viewLabel = detailLabel(Component.literal(view.name()));
            final BlueprintPreviewCanvas canvas = new BlueprintPreviewCanvas(entry, view);
            canvas.layout(layout -> {
                layout.width(PREVIEW_DIALOG_INNER_WIDTH - 20);
                layout.height(PREVIEW_CANVAS_HEIGHT);
                layout.paddingAll(4);
            });
            canvas.style(style -> style.backgroundTexture(fieldTexture()));

            final Button previous = navButton(tr("ui.preview_previous"));
            previous.setOnClick(event -> {
                BlueprintToolClientSession.previousBlueprintPreviewView();
                sync();
            });

            final Button next = navButton(tr("ui.preview_next"));
            next.setOnClick(event -> {
                BlueprintToolClientSession.nextBlueprintPreviewView();
                sync();
            });

            final UIElement switchRow = new UIElement()
                    .layout(layout -> {
                        layout.width(PREVIEW_DIALOG_INNER_WIDTH - 20);
                        layout.height(ROW_HEIGHT);
                        layout.flexDirection(FlexDirection.ROW);
                        layout.alignItems(AlignItems.STRETCH);
                        layout.justifyContent(AlignContent.SPACE_EVENLY);
                    })
                    .addChildren(previous, next);

            return new UIElement()
                    .layout(layout -> {
                        layout.width(PREVIEW_DIALOG_INNER_WIDTH - 20);
                        layout.heightAuto();
                        layout.flexDirection(FlexDirection.COLUMN);
                        layout.gapAll(GAP);
                    })
                    .addChildren(viewLabel, canvas, switchRow);
        }

        private UIElement authorColumn() {
            final boolean editing = BlueprintToolClientSession.blueprintMetadataEditing();
            final String author = editing
                    ? BlueprintToolClientSession.draftBlueprintAuthor()
                    : BlueprintToolClientSession.openedBlueprintMetadata().author();
            final String description = editing
                    ? BlueprintToolClientSession.draftBlueprintDescription()
                    : BlueprintToolClientSession.openedBlueprintMetadata().description();

            final Label descriptionLabel = detailLabel(tr("ui.description"));
            final UIElement descriptionField = editing
                    ? editableDescription(description)
                    : descriptionViewer(description);
            final UIElement authorRow = editing
                    ? editableAuthorRow(author)
                    : authorRow(author);
            final UIElement editRow = editConfigRow(editing);

            return new UIElement()
                    .layout(layout -> {
                        layout.width(PREVIEW_DIALOG_INNER_WIDTH - 20);
                        layout.heightAuto();
                        layout.flexDirection(FlexDirection.COLUMN);
                        layout.gapAll(GAP);
                    })
                    .addChildren(descriptionLabel, descriptionField, authorRow, editRow);
        }

        private UIElement descriptionViewer(final String description) {
            final ScrollerView scroller = new ScrollerView();
            scroller.layout(layout -> {
                layout.width(PREVIEW_DIALOG_INNER_WIDTH - 20);
                layout.height(DESCRIPTION_HEIGHT);
            });
            scroller.style(style -> style.backgroundTexture(fieldTexture()));
            scroller.viewContainer(container -> container.layout(layout -> {
                layout.width(PREVIEW_DIALOG_INNER_WIDTH - 32);
                layout.heightAuto();
                layout.paddingAll(3);
            }));

            final Label text = bareLabel(Horizontal.LEFT);
            text.setText(description == null || description.isBlank()
                    ? tr("ui.description_empty")
                    : Component.literal(description));
            text.textStyle(style -> style
                    .adaptiveHeight(true)
                    .textWrap(TextWrap.WRAP)
                    .textAlignVertical(Vertical.TOP));
            text.layout(layout -> {
                layout.width(PREVIEW_DIALOG_INNER_WIDTH - 36);
                layout.heightAuto();
            });
            scroller.addScrollViewChild(text);
            return scroller;
        }

        private TextArea editableDescription(final String description) {
            final TextArea area = textArea(
                    "sable_blueprint_description",
                    PREVIEW_DIALOG_INNER_WIDTH - 20,
                    DESCRIPTION_HEIGHT
            );
            area.setLines(lines(description));
            area.setLinesResponder(lines -> BlueprintToolClientSession.setDraftBlueprintDescription(joinLines(lines)));
            return area;
        }

        private UIElement authorRow(final String author) {
            final Label label = detailLabel(tr("ui.author"));
            label.layout(layout -> {
                layout.width(64);
                layout.height(ROW_HEIGHT);
            });

            final Label value = detailLabel(author == null || author.isBlank()
                    ? tr("ui.author_empty")
                    : Component.literal(author));
            value.layout(layout -> {
                layout.width(PREVIEW_DIALOG_INNER_WIDTH - 20 - 64 - GAP);
                layout.height(ROW_HEIGHT);
            });

            return row(label, value);
        }

        private UIElement editableAuthorRow(final String author) {
            final Label label = detailLabel(tr("ui.author"));
            label.layout(layout -> {
                layout.width(64);
                layout.height(ROW_HEIGHT);
            });

            final TextField field = textField(
                    "sable_blueprint_author",
                    PREVIEW_DIALOG_INNER_WIDTH - 20 - 64 - GAP
            );
            field.setText(author == null ? "" : author);
            field.setTextResponder(BlueprintToolClientSession::setDraftBlueprintAuthor);
            return row(label, field);
        }

        private UIElement editConfigRow(final boolean editing) {
            final Button edit = navButton(editing ? tr("ui.save_metadata") : tr("ui.edit_metadata"));
            edit.setOnClick(event -> {
                if (editing) {
                    BlueprintToolClientSession.saveBlueprintMetadataEdit(
                            BlueprintToolClientSession.draftBlueprintAuthor(),
                            BlueprintToolClientSession.draftBlueprintDescription()
                    );
                } else {
                    BlueprintToolClientSession.beginBlueprintMetadataEdit();
                }
                sync();
            });

            final UIElement row = new UIElement()
                    .layout(layout -> {
                        layout.width(PREVIEW_DIALOG_INNER_WIDTH - 20);
                        layout.height(ROW_HEIGHT);
                        layout.flexDirection(FlexDirection.ROW);
                        layout.alignItems(AlignItems.STRETCH);
                        layout.justifyContent(AlignContent.SPACE_EVENLY);
                    })
                    .addChild(edit);

            if (editing) {
                final Button discard = navButton(tr("ui.discard_metadata"));
                discard.setOnClick(event -> {
                    BlueprintToolClientSession.discardBlueprintMetadataEdit();
                    sync();
                });
                row.addChild(discard);
            }

            return row;
        }

        private UIElement row(final UIElement left, final UIElement right) {
            return new UIElement()
                    .layout(layout -> {
                        layout.width(PREVIEW_DIALOG_INNER_WIDTH - 20);
                        layout.height(ROW_HEIGHT);
                        layout.flexDirection(FlexDirection.ROW);
                        layout.alignItems(AlignItems.STRETCH);
                        layout.gapAll(GAP);
                    })
                    .addChildren(left, right);
        }

        private Button navButton(final Component text) {
            final Button button = textButton(text);
            button.layout(layout -> {
                layout.width(76);
                layout.height(ROW_HEIGHT);
            });
            return button;
        }

        private Label detailLabel(final Component text) {
            final Label label = bareLabel(Horizontal.LEFT);
            label.setText(text);
            label.layout(layout -> {
                layout.width(PREVIEW_DIALOG_INNER_WIDTH - 20);
                layout.height(ROW_HEIGHT);
            });
            return label;
        }

        private List<String> lines(final String text) {
            if (text == null || text.isEmpty()) {
                return List.of("");
            }
            return Arrays.asList(text.split("\\R", -1));
        }

        private String joinLines(final String[] lines) {
            return lines == null ? "" : String.join("\n", lines);
        }
    }

    private static final class BlueprintPreviewCanvas extends UIElement {
        private final BlueprintToolLocalFiles.Entry entry;
        private final SableBlueprintPreview.View view;

        private BlueprintPreviewCanvas(final BlueprintToolLocalFiles.Entry entry, final SableBlueprintPreview.View view) {
            this.entry = entry;
            this.view = view;
        }

        @Override
        public void drawBackgroundAdditional(final GUIContext guiContext) {
            final SableBlueprintPreview preview = BlueprintToolLocalPreviewCache.preview(this.entry);
            final int x = Math.round(getContentX());
            final int y = Math.round(getContentY());
            final int width = Math.round(getContentWidth());
            final int height = Math.round(getContentHeight());
            final int size = Math.max(1, Math.min(width, height));
            final int startX = x + Math.max(0, (width - size) / 2);
            final int startY = y + Math.max(0, (height - size) / 2);
            drawLargePreviewTile(guiContext, preview, this.view, startX, startY, size);
        }
    }

    private static final class RefreshingBlueprintList extends ScrollerView {
        private final Player player;
        private final float rowWidth;
        private int revision = Integer.MIN_VALUE;

        private RefreshingBlueprintList(final Player player, final float width) {
            this.player = player;
            this.rowWidth = width - 12;
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
                addScrollViewChild(emptyRow(this.rowWidth));
                return;
            }

            for (final BlueprintToolLocalFiles.Entry entry : entries) {
                addScrollViewChild(fileRow(this.player, entry, this.rowWidth));
            }
        }
    }

    private static final class RefreshingSubLevelList extends ScrollerView {
        private final Player player;
        private int revision = Integer.MIN_VALUE;

        private RefreshingSubLevelList(final Player player) {
            this.player = player;
            refreshIfNeeded();
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
                addScrollViewChild(emptySubLevelRow());
                return;
            }

            for (final BlueprintToolSubLevelGroup group : groups) {
                addScrollViewChild(subLevelGroup(this.player, group));
            }
        }
    }

    private static final class SubLevelDetailDialog extends UIElement {
        private UUID renderedUuid;
        private int renderedRevision = Integer.MIN_VALUE;

        private SubLevelDetailDialog() {
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

            final Label uuid = detailLabel(tr("ui.sublevel_uuid", entry.uuid().toString()));
            final Label name = detailLabel(tr("ui.sublevel_name", entry.displayName()));
            final Label pos = detailLabel(tr("ui.sublevel_pos", positionText(entry), entry.distanceLabel()));

            final Button teleport = actionButton(tr("ui.sublevel_tp_player"));
            teleport.setOnClick(event -> BlueprintToolClientSession.requestTeleportPlayerToSubLevel(entry));

            final Button bring = actionButton(entry.groupSize() > 1
                    ? tr("ui.sublevel_bring_group", entry.groupSize())
                    : tr("ui.sublevel_bring"));
            bring.setOnClick(event -> BlueprintToolClientSession.requestBringSubLevel(entry));

            final Button toggleStatic = actionButton(entry.staticLocked()
                    ? tr("ui.sublevel_switch_non_static")
                    : tr("ui.sublevel_switch_static"));
            toggleStatic.setOnClick(event -> BlueprintToolClientSession.requestToggleStatic(entry));

            final TextField rename = textField("sable_blueprint_sublevel_rename", CONTENT_WIDTH - 16 - GAP - RENAME_BUTTON_WIDTH);
            rename.setText(entry.name() == null ? "" : entry.name());
            final Button renameOk = textButton(tr("ui.ok"));
            renameOk.layout(layout -> {
                layout.width(RENAME_BUTTON_WIDTH);
                layout.height(ROW_HEIGHT);
            });
            renameOk.setOnClick(event -> BlueprintToolClientSession.requestRenameSubLevel(entry, rename.getText()));

            final UIElement renameRow = new UIElement()
                    .layout(layout -> {
                        layout.width(CONTENT_WIDTH - 16);
                        layout.height(ROW_HEIGHT);
                        layout.flexDirection(FlexDirection.ROW);
                        layout.gapAll(GAP);
                    })
                    .addChildren(rename, renameOk);

            final Button close = actionButton(tr("ui.close"));
            close.setOnClick(event -> {
                BlueprintToolClientSession.closeSubLevelDetail();
                sync();
            });

            addChildren(uuid, name, pos, teleport, bring, toggleStatic, renameRow, close);
        }

        private Button actionButton(final Component text) {
            final Button button = textButton(text);
            button.layout(layout -> {
                layout.width(CONTENT_WIDTH - 16);
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
}
