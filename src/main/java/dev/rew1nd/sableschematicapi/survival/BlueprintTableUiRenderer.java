package dev.rew1nd.sableschematicapi.survival;

import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintSummary;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles.Entry;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

public final class BlueprintTableUiRenderer {
    private static final float ROOT_WIDTH = 204;
    private static final float GAP = 4;
    private static final float ROW_HEIGHT = 14;
    private static final float SLOT_SIZE = 18;
    private static final float PLAYER_INVENTORY_HEIGHT = SLOT_SIZE * 4 + 5;
    private static final float DROPDOWN_HEIGHT = SLOT_SIZE * 3 + 2;
    private static final int TEXT_COLOR = ColorPattern.WHITE.color;
    private static final int MUTED_TEXT_COLOR = ColorPattern.LIGHT_GRAY.color;
    private static final int ROOT_COLOR = ColorPattern.BLACK.color;
    private static final int PANEL_COLOR = ColorPattern.SEAL_BLACK.color;
    private static final int FIELD_COLOR = ColorPattern.DARK_GRAY.color;
    private static final int BORDER_COLOR = ColorPattern.GRAY.color;
    private static final int BUTTON_COLOR = ColorPattern.DARK_GRAY.color;
    private static final int BUTTON_HOVER_COLOR = ColorPattern.T_BLUE.color;
    private static final int BUTTON_PRESSED_COLOR = ColorPattern.SEAL_BLACK.color;

    private static String selectedFileName = "";

    private BlueprintTableUiRenderer() {
    }

    public static ModularUI render(final BlueprintTableBlockEntity table, final Player player) {
        selectedFileName = "";

        final UIElement root = new UIElement()
                .setId("blueprint_table_root")
                .layout(layout -> {
                    layout.width(ROOT_WIDTH);
                    layout.heightAuto();
                    layout.paddingAll(6);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.alignItems(AlignItems.STRETCH);
                    layout.gapAll(GAP);
                })
                .style(style -> style.backgroundTexture(panelBackground()));

        root.addChildren(
                header(table, player),
                selectionRow(table, player),
                summaryPanel(table),
                playerInventoryPanel()
        );

        return ModularUI.of(
                UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC))),
                player
        );
    }

    private static UIElement header(final BlueprintTableBlockEntity table, final Player player) {
        final UIElement row = row("blueprint_table_header");
        final Label title = label(Horizontal.LEFT, TEXT_COLOR);
        title.setText(Component.translatable("block.sable_schematic_api.blueprint_table"));
        title.layout(layout -> {
            layout.flex(1);
            layout.height(ROW_HEIGHT);
        });

        final Button eject = iconButton(
                Icons.EXPORT,
                Component.translatable("sable_schematic_api.blueprint_table.ui.eject")
        );
        eject.setOnServerClick(event -> {
            final ItemStack ejected = table.ejectBlueprint();
            if (!ejected.isEmpty() && !player.getInventory().add(ejected)) {
                player.drop(ejected, false);
            }
        });

        row.addChildren(title, eject);
        return row;
    }

    private static UIElement selectionRow(final BlueprintTableBlockEntity table, final Player player) {
        final UIElement row = row("blueprint_table_selection_row");
        row.layout(layout -> layout.heightAuto());

        final BlueprintFileDropdown dropdown = new BlueprintFileDropdown(ROOT_WIDTH - SLOT_SIZE - 20 - GAP * 3);
        dropdown.layout(layout -> {
            layout.width(ROOT_WIDTH - SLOT_SIZE - 20 - GAP * 3);
            layout.height(DROPDOWN_HEIGHT);
        });

        final ItemHandlerSlot handlerSlot = new ItemHandlerSlot(table.inventory(), BlueprintTableBlockEntity.BLUEPRINT_SLOT)
                .setCanPlace(stack -> table.inventory().isItemValid(BlueprintTableBlockEntity.BLUEPRINT_SLOT, stack));
        final ItemSlot itemSlot = new ItemSlot(handlerSlot);
        itemSlot.setId("blueprint_table_slot");
        itemSlot.slotStyle(style -> style
                .quickMovePriority(100)
                .acceptQuickMove(true)
                .isPlayerSlot(false));
        itemSlot.layout(layout -> {
            layout.width(SLOT_SIZE);
            layout.height(SLOT_SIZE);
        });

        final Button upload = iconButton(
                Icons.SAVE,
                Component.translatable("sable_schematic_api.blueprint_table.ui.upload")
        );
        upload.setOnClick(event -> {
            if (selectedFileName.isBlank()) {
                return;
            }
            BlueprintTableUploadHandler.requestUpload(selectedFileName);
        });
        upload.layout(layout -> {
            layout.width(20);
            layout.height(SLOT_SIZE);
        });

        row.addChildren(dropdown, itemSlot, upload);
        return row;
    }

    private static UIElement summaryPanel(final BlueprintTableBlockEntity table) {
        final UIElement panel = new UIElement()
                .setId("blueprint_table_summary_panel")
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.heightAuto();
                    layout.paddingAll(4);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(2);
                })
                .style(style -> style.backgroundTexture(displayBackground()));

        final StatusLabel summary = new StatusLabel(() -> {
            final ItemStack stack = table.inventory().getStackInSlot(BlueprintTableBlockEntity.BLUEPRINT_SLOT);
            if (BlueprintDataItem.hasPayload(stack)) {
                final BlueprintSummary s = BlueprintDataItem.summary(stack);
                return Component.translatable(
                        "sable_schematic_api.blueprint_table.ui.summary",
                        s.subLevels(), s.blocks(), s.blockEntityTags(), s.entities()
                );
            }
            if (!BlueprintDataItem.isBlueprintItem(stack)) {
                return Component.translatable("sable_schematic_api.blueprint_table.ui.insert_hint");
            }
            return Component.translatable("sable_schematic_api.blueprint_table.ui.empty_blueprint");
        }, MUTED_TEXT_COLOR);
        summary.layout(layout -> {
            layout.widthPercent(100);
            layout.height(ROW_HEIGHT);
        });

        panel.addChildren(summary);
        return panel;
    }

    private static UIElement playerInventoryPanel() {
        final UIElement panel = new UIElement()
                .setId("blueprint_table_player_inventory_panel")
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.heightAuto();
                    layout.paddingAll(4);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(2);
                })
                .style(style -> style.backgroundTexture(displayBackground()));

        final InventorySlots inventory = new InventorySlots();
        inventory.setId("blueprint_table_player_inventory");
        inventory.apply(slot -> slot.slotStyle(style -> style
                .slotOverlay(ItemSlot.ITEM_SLOT_TEXTURE.copy())
                .showSlotOverlayOnlyEmpty(true)
                .quickMovePriority(0)
                .acceptQuickMove(true)
                .isPlayerSlot(true)));
        inventory.layout(layout -> {
            layout.width(SLOT_SIZE * 9);
            layout.height(PLAYER_INVENTORY_HEIGHT);
            layout.alignSelf(AlignItems.CENTER);
        });

        panel.addChildren(
                sectionLabel(Component.translatable("sable_schematic_api.blueprint_table.ui.player_inventory")),
                inventory
        );
        return panel;
    }

    private static Label sectionLabel(final Component text) {
        final Label label = label(Horizontal.LEFT, TEXT_COLOR);
        label.setText(text);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(ROW_HEIGHT);
        });
        return label;
    }

    private static UIElement row(final String id) {
        return new UIElement()
                .setId(id)
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.height(ROW_HEIGHT);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.CENTER);
                    layout.gapAll(3);
                });
    }

    private static Label label(final Horizontal align, final int color) {
        final Label label = new Label();
        label.setText(Component.empty());
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textWrap(TextWrap.HOVER_ROLL)
                .rollSpeed(1.0F)
                .textColor(color)
                .textShadow(false)
                .fontSize(8)
                .textAlignHorizontal(align)
                .textAlignVertical(Vertical.CENTER));
        label.style(style -> style.overflowVisible(false));
        return label;
    }

    private static Button iconButton(final IGuiTexture icon, final Component tooltip) {
        final Button button = new Button();
        button.noText();
        button.addPreIcon(icon);
        button.style(style -> style.tooltips(tooltip));
        button.buttonStyle(style -> style
                .baseTexture(buttonTexture(BUTTON_COLOR, BORDER_COLOR))
                .hoverTexture(buttonTexture(BUTTON_HOVER_COLOR, BORDER_COLOR))
                .pressedTexture(buttonTexture(BUTTON_PRESSED_COLOR, BORDER_COLOR)));
        button.layout(layout -> {
            layout.width(18);
            layout.height(ROW_HEIGHT);
            layout.paddingAll(0);
        });
        return button;
    }

    private static IGuiTexture panelBackground() {
        return SDFRectTexture.of(ROOT_COLOR)
                .setBorderColor(BORDER_COLOR)
                .setRadius(2)
                .setStroke(1);
    }

    private static IGuiTexture displayBackground() {
        return SDFRectTexture.of(PANEL_COLOR)
                .setBorderColor(BORDER_COLOR)
                .setRadius(2)
                .setStroke(1);
    }

    private static IGuiTexture buttonTexture(final int fill, final int border) {
        return SDFRectTexture.of(fill)
                .setBorderColor(border)
                .setRadius(2)
                .setStroke(1);
    }

    /**
     * A compact scroller view that lists local blueprint files and tracks selection.
     */
    private static final class BlueprintFileDropdown extends ScrollerView {
        private int revision = -1;

        private BlueprintFileDropdown(final float width) {
            this.setId("blueprint_table_file_dropdown");
            this.style(style -> style.backgroundTexture(displayBackground()));
            this.viewContainer(container -> container.layout(layout -> {
                layout.width(width - 6);
                layout.heightAuto();
                layout.flexDirection(FlexDirection.COLUMN);
                layout.gapAll(0);
            }));
            BlueprintTableClientData.refreshLocalFiles();
            this.refreshEntries();
        }

        @Override
        public void screenTick() {
            super.screenTick();
            final int currentRevision = BlueprintTableClientData.fileListRevision();
            if (currentRevision != this.revision) {
                this.revision = currentRevision;
                this.refreshEntries();
            }
        }

        private void refreshEntries() {
            clearAllScrollViewChildren();

            final List<Entry> files = BlueprintTableClientData.localFiles();
            if (files.isEmpty()) {
                final Label empty = new Label();
                empty.setText(Component.translatable("sable_schematic_api.blueprint_table.ui.no_local_files")
                        .withStyle(ChatFormatting.GRAY));
                empty.textStyle(style -> style
                        .fontSize(8)
                        .textColor(ColorPattern.LIGHT_GRAY.color)
                        .textShadow(false));
                empty.layout(layout -> {
                    layout.widthPercent(100);
                    layout.height(ROW_HEIGHT);
                });
                addScrollViewChild(empty);
                return;
            }

            for (final Entry file : files) {
                final Button entry = new Button();
                entry.setText(Component.literal(file.name()));
                entry.textStyle(style -> style
                        .fontSize(8)
                        .textColor(TEXT_COLOR)
                        .textShadow(false));
                entry.buttonStyle(style -> {
                    if (file.name().equals(selectedFileName)) {
                        style.baseTexture(SDFRectTexture.of(BUTTON_HOVER_COLOR).setRadius(1));
                    } else {
                        style.baseTexture(SDFRectTexture.of(FIELD_COLOR).setRadius(1));
                    }
                    style.hoverTexture(SDFRectTexture.of(BUTTON_HOVER_COLOR).setRadius(1));
                    style.pressedTexture(SDFRectTexture.of(BUTTON_PRESSED_COLOR).setRadius(1));
                });
                entry.layout(layout -> {
                    layout.widthPercent(100);
                    layout.height(ROW_HEIGHT);
                });
                entry.setOnClick(e -> {
                    selectedFileName = file.name();
                    this.refreshEntries();
                });
                addScrollViewChild(entry);
            }
        }
    }

    private static final class StatusLabel extends Label {
        private final Supplier<Component> supplier;

        private StatusLabel(final Supplier<Component> supplier, final int color) {
            this.supplier = supplier;
            this.setText(supplier.get());
            this.textStyle(style -> style
                    .adaptiveWidth(false)
                    .adaptiveHeight(false)
                    .textWrap(TextWrap.HOVER_ROLL)
                    .rollSpeed(1.0F)
                    .textColor(color)
                    .textShadow(false)
                    .fontSize(8)
                    .textAlignHorizontal(Horizontal.LEFT)
                    .textAlignVertical(Vertical.CENTER));
            this.style(style -> {
                style.backgroundTexture(SDFRectTexture.of(FIELD_COLOR).setRadius(1));
                style.overflowVisible(false);
            });
        }

        @Override
        public void screenTick() {
            super.screenTick();
            this.setText(this.supplier.get());
        }
    }
}
