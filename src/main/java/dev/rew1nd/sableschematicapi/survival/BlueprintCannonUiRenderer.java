package dev.rew1nd.sableschematicapi.survival;

import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
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
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public final class BlueprintCannonUiRenderer {
    private static final float LEFT_PANEL_WIDTH = 160;
    private static final float RIGHT_PANEL_WIDTH = 204;
    private static final float GAP = 4;
    private static final float ROOT_WIDTH = LEFT_PANEL_WIDTH + RIGHT_PANEL_WIDTH + GAP + 12;
    private static final float ROW_HEIGHT = 14;
    private static final float SLOT_SIZE = 18;
    private static final float BUDGET_HEADER_HEIGHT = ROW_HEIGHT;
    private static final float PLAYER_INVENTORY_HEIGHT = SLOT_SIZE * 4 + 5;
    private static final float INNER_GAP = 2;
    private static final float PANEL_PADDING = 4;
    private static final float BUDGET_PANEL_PADDING = 3;
    private static final float STATUS_PANEL_HEIGHT = ROW_HEIGHT * 4 + INNER_GAP * 3 + PANEL_PADDING * 2;
    private static final float BLUEPRINT_PANEL_HEIGHT = ROW_HEIGHT + SLOT_SIZE + INNER_GAP + PANEL_PADDING * 2;
    private static final float PLAYER_INVENTORY_PANEL_HEIGHT = ROW_HEIGHT + PLAYER_INVENTORY_HEIGHT + INNER_GAP + PANEL_PADDING * 2;
    private static final float RIGHT_PANEL_HEIGHT = ROW_HEIGHT + STATUS_PANEL_HEIGHT + BLUEPRINT_PANEL_HEIGHT + PLAYER_INVENTORY_PANEL_HEIGHT + GAP * 3;
    private static final float BUDGET_LIST_HEIGHT = RIGHT_PANEL_HEIGHT - BUDGET_HEADER_HEIGHT - GAP - BUDGET_PANEL_PADDING * 2;
    private static final float BUDGET_ROW_WIDTH = LEFT_PANEL_WIDTH - BUDGET_PANEL_PADDING * 2 - 10;
    private static final int TEXT_COLOR = ColorPattern.WHITE.color;
    private static final int MUTED_TEXT_COLOR = ColorPattern.LIGHT_GRAY.color;
    private static final int ROOT_COLOR = ColorPattern.BLACK.color;
    private static final int PANEL_COLOR = ColorPattern.SEAL_BLACK.color;
    private static final int FIELD_COLOR = ColorPattern.DARK_GRAY.color;
    private static final int BORDER_COLOR = ColorPattern.GRAY.color;
    private static final int BUTTON_COLOR = ColorPattern.DARK_GRAY.color;
    private static final int BUTTON_HOVER_COLOR = ColorPattern.T_BLUE.color;
    private static final int BUTTON_PRESSED_COLOR = ColorPattern.SEAL_BLACK.color;

    private BlueprintCannonUiRenderer() {
    }

    public static ModularUI render(final BlueprintCannonBlockEntity blockEntity, final Player player) {
        final UIElement root = new UIElement()
                .setId("blueprint_cannon_root")
                .layout(layout -> {
                    layout.widthAuto();
                    layout.heightAuto();
                    layout.paddingAll(6);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.STRETCH);
                    layout.gapAll(GAP);
                })
                .style(style -> style.backgroundTexture(panelBackground()));

        root.addChildren(
                budgetPanel(blockEntity),
                rightPanel(blockEntity, player)
        );

        return ModularUI.of(
                UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC))),
                player
        );
    }

    private static UIElement budgetPanel(final BlueprintCannonBlockEntity blockEntity) {




        final UIElement left = new UIElement()
                .setId("blueprint_cannon_budget_container")
                .layout(layout -> {
                    layout.width(LEFT_PANEL_WIDTH);
                    layout.heightAuto();
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.alignItems(AlignItems.STRETCH);
                    layout.gapAll(GAP);
                });




        final UIElement row = row("blueprint_cannon_header_left");
        row.layout(layout -> layout.height(BUDGET_HEADER_HEIGHT));
        final Label title = label(Horizontal.LEFT, TEXT_COLOR);
        title.setText(Component.translatable("block.sable_schematic_api.blueprint_budget"));
        title.layout(layout -> {
            layout.flex(1);
            layout.height(BUDGET_HEADER_HEIGHT);
        });

        final ItemSlot clipboardSlot = createClipboardSlot(blockEntity);
        final Button writeClipboard = createWriteClipboardButton(blockEntity);
        final Button refresh = createRefreshButton(blockEntity);

        row.addChildren(title, clipboardSlot, writeClipboard, refresh);

        final UIElement panel = new UIElement()
            .setId("blueprint_cannon_budget_panel")
            .layout(layout -> {
                layout.widthAuto();
                layout.height(BUDGET_LIST_HEIGHT + BUDGET_PANEL_PADDING * 2);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.gapAll(0);
                layout.paddingAll(BUDGET_PANEL_PADDING);
            })
            .style(style -> style.backgroundTexture(displayBackground()));

        final RefreshableBudgetList budgetList = new RefreshableBudgetList(blockEntity);
        budgetList.layout(layout -> {
            layout.widthAuto();
            layout.height(BUDGET_LIST_HEIGHT);
        });

        panel.addChildren(
            budgetList
        );

        left.addChildren(row, panel);

        return left;
    }

    private static @NotNull Button createRefreshButton(BlueprintCannonBlockEntity blockEntity) {
        final Button refresh = new Button();
        refresh.setText(Component.literal("↻"));
        refresh.style(style -> style.tooltips(
                Component.translatable("sable_schematic_api.blueprint_cannon.ui.refresh_budget")));
        refresh.buttonStyle(style -> style
                .baseTexture(buttonTexture(BUTTON_COLOR, BORDER_COLOR))
                .hoverTexture(buttonTexture(BUTTON_HOVER_COLOR, BORDER_COLOR))
                .pressedTexture(buttonTexture(BUTTON_PRESSED_COLOR, BORDER_COLOR)));
        refresh.setOnServerClick(event -> blockEntity.refreshEstimatedBudget());
        refresh.layout(layout -> {
            layout.width(18);
            layout.height(SLOT_SIZE);
            layout.flexShrink(0);
            layout.paddingAll(0);
        });
        return refresh;
    }

    private static @NotNull ItemSlot createClipboardSlot(final BlueprintCannonBlockEntity blockEntity) {
        final ItemHandlerSlot handlerSlot = new ItemHandlerSlot(blockEntity.inventory(), BlueprintCannonBlockEntity.CLIPBOARD_SLOT)
                .setCanPlace(stack -> blockEntity.inventory().isItemValid(BlueprintCannonBlockEntity.CLIPBOARD_SLOT, stack));
        final ItemSlot itemSlot = new ItemSlot(handlerSlot);
        itemSlot.setId("blueprint_cannon_clipboard_slot");
        itemSlot.slotStyle(style -> style
                .quickMovePriority(90)
                .acceptQuickMove(true)
                .isPlayerSlot(false));
        itemSlot.layout(layout -> {
            layout.width(SLOT_SIZE);
            layout.height(SLOT_SIZE);
            layout.flexShrink(0);
        });
        return itemSlot;
    }

    private static @NotNull Button createWriteClipboardButton(final BlueprintCannonBlockEntity blockEntity) {
        final Button write = iconButton(
                Icons.SAVE,
                Component.translatable("sable_schematic_api.blueprint_cannon.ui.write_clipboard")
        );
        write.setOnServerClick(event -> blockEntity.writeBudgetToClipboard());
        write.layout(layout -> {
            layout.width(SLOT_SIZE);
            layout.height(SLOT_SIZE);
            layout.flexShrink(0);
            layout.paddingAll(0);
        });
        return write;
    }

    private static UIElement rightPanel(final BlueprintCannonBlockEntity blockEntity, final Player player) {
        final UIElement panel = new UIElement()
                .setId("blueprint_cannon_right_panel")
                .layout(layout -> {
                    layout.width(RIGHT_PANEL_WIDTH);
                    layout.heightAuto();
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.alignItems(AlignItems.STRETCH);
                    layout.gapAll(GAP);
                });

        panel.addChildren(
                header(blockEntity, player),
                statusPanel(blockEntity),
                blueprintPanel(blockEntity),
                playerInventoryPanel()
        );
        return panel;
    }

    private static UIElement header(final BlueprintCannonBlockEntity blockEntity, final Player player) {
        final UIElement row = row("blueprint_cannon_header");
        final Label title = label(Horizontal.LEFT, TEXT_COLOR);
        title.setText(Component.translatable("block.sable_schematic_api.blueprint_cannon"));
        title.layout(layout -> {
            layout.flex(1);
            layout.height(ROW_HEIGHT);
        });

        final Button startPause = iconButton(
                blockEntity.isActive() ? Icons.PLAY_PAUSE : Icons.PLAY,
                Component.translatable("sable_schematic_api.blueprint_cannon.ui.toggle")
        );
        startPause.setOnServerClick(event -> blockEntity.setActive(!blockEntity.isActive()));

        final Button eject = iconButton(
                Icons.EXPORT,
                Component.translatable("sable_schematic_api.blueprint_cannon.ui.eject")
        );
        eject.setOnServerClick(event -> blockEntity.ejectBlueprint(player));

        final Button reset = iconButton(
                Icons.REPLAY,
                Component.translatable("sable_schematic_api.blueprint_cannon.ui.reset")
        );
        reset.setOnServerClick(event -> blockEntity.resetProgress());

        row.addChildren(title, startPause, eject, reset);
        return row;
    }

    private static UIElement statusPanel(final BlueprintCannonBlockEntity blockEntity) {
        final UIElement panel = new UIElement()
                .setId("blueprint_cannon_status_panel")
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.heightAuto();
                    layout.paddingAll(4);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(2);
                })
                .style(style -> style.backgroundTexture(displayBackground()));

        panel.addChildren(
                statusLabel(blockEntity::statusLine, TEXT_COLOR),
                statusLabel(blockEntity::progressLine, MUTED_TEXT_COLOR),
                statusLabel(blockEntity::sourceLine, MUTED_TEXT_COLOR),
                //speedRow(blockEntity),
                missingRow(blockEntity)
        );
        return panel;
    }

    private static UIElement missingRow(final BlueprintCannonBlockEntity blockEntity) {
        final UIElement row = row("blueprint_cannon_missing_row");
        final Button skip = textButton(
                Component.literal(">"),
                Component.translatable("sable_schematic_api.blueprint_cannon.ui.skip_current")
        );
        skip.setOnServerClick(event -> blockEntity.skipCurrentBlock());

        final StatusLabel missing = statusLabel(blockEntity::missingLine, MUTED_TEXT_COLOR);
        missing.layout(layout -> {
            layout.flex(1);
            layout.height(ROW_HEIGHT);
        });

        row.addChildren(skip, missing);
        return row;
    }

    private static UIElement blueprintPanel(final BlueprintCannonBlockEntity blockEntity) {
        final UIElement panel = new UIElement()
                .setId("blueprint_cannon_blueprint_panel")
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.heightAuto();
                    layout.paddingAll(4);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(2);
                })
                .style(style -> style.backgroundTexture(displayBackground()));

        panel.addChildren(
                statusLabel(blockEntity::blueprintLine, MUTED_TEXT_COLOR),
                blueprintSlotRow(blockEntity)
        );
        return panel;
    }

    private static UIElement blueprintSlotRow(final BlueprintCannonBlockEntity blockEntity) {
        final UIElement row = row("blueprint_cannon_blueprint_slot_row");
        row.layout(layout -> layout.height(SLOT_SIZE));

        final ItemHandlerSlot handlerSlot = new ItemHandlerSlot(blockEntity.inventory(), BlueprintCannonBlockEntity.BLUEPRINT_SLOT)
                .setCanPlace(stack -> blockEntity.inventory().isItemValid(BlueprintCannonBlockEntity.BLUEPRINT_SLOT, stack));
        final ItemSlot itemSlot = new ItemSlot(handlerSlot);
        itemSlot.setId("blueprint_cannon_blueprint_slot");
        itemSlot.slotStyle(style -> style
                .quickMovePriority(100)
                .acceptQuickMove(true)
                .isPlayerSlot(false));
        itemSlot.layout(layout -> {
            layout.width(SLOT_SIZE);
            layout.height(SLOT_SIZE);
        });

        final StatusLabel hint = new StatusLabel(blockEntity::insertHintLine, MUTED_TEXT_COLOR);
        hint.layout(layout -> {
            layout.flex(1);
            layout.height(SLOT_SIZE);
        });

        row.addChildren(itemSlot, hint);
        return row;
    }

    private static UIElement speedRow(final BlueprintCannonBlockEntity blockEntity) {
        final UIElement row = row("blueprint_cannon_speed_row");
        final StatusLabel speed = statusLabel(blockEntity::speedLine, MUTED_TEXT_COLOR);
        speed.layout(layout -> {
            layout.flex(1);
            layout.height(ROW_HEIGHT);
        });

        final Button slower = iconButton(
                Icons.REMOVE,
                Component.translatable("sable_schematic_api.blueprint_cannon.ui.speed_down")
        );
        slower.setOnServerClick(event -> blockEntity.debugSlowDown());

        final Button faster = iconButton(
                Icons.ADD,
                Component.translatable("sable_schematic_api.blueprint_cannon.ui.speed_up")
        );
        faster.setOnServerClick(event -> blockEntity.debugSpeedUp());

        row.addChildren(speed, slower, faster);
        return row;
    }

    private static UIElement playerInventoryPanel() {
        final UIElement panel = new UIElement()
                .setId("blueprint_cannon_player_inventory_panel")
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.heightAuto();
                    layout.paddingAll(4);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(2);
                })
                .style(style -> style.backgroundTexture(displayBackground()));

        final InventorySlots inventory = new InventorySlots();
        inventory.setId("blueprint_cannon_player_inventory");
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
                sectionLabel(Component.translatable("sable_schematic_api.blueprint_cannon.ui.player_inventory")),
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

    private static StatusLabel statusLabel(final Supplier<Component> supplier, final int color) {
        final StatusLabel label = new StatusLabel(supplier, color);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(ROW_HEIGHT);
        });
        return label;
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

    private static Button textButton(final Component text, final Component tooltip) {
        final Button button = new Button();
        button.setText(text);
        button.style(style -> style.tooltips(tooltip));
        button.buttonStyle(style -> style
                .baseTexture(buttonTexture(BUTTON_COLOR, BORDER_COLOR))
                .hoverTexture(buttonTexture(BUTTON_HOVER_COLOR, BORDER_COLOR))
                .pressedTexture(buttonTexture(BUTTON_PRESSED_COLOR, BORDER_COLOR)));
        button.layout(layout -> {
            layout.width(18);
            layout.height(ROW_HEIGHT);
            layout.flexShrink(0);
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

    private static final class RefreshableBudgetList extends ScrollerView {
        private final BlueprintCannonBlockEntity blockEntity;
        private int lastBudgetSize = -1;
        private int lastRefreshTick = 0;

        private RefreshableBudgetList(final BlueprintCannonBlockEntity blockEntity) {
            this.blockEntity = blockEntity;
            this.setId("blueprint_cannon_budget_list");
            this.viewContainer(container -> container.layout(layout -> {
                layout.width(BUDGET_ROW_WIDTH);
                layout.heightAuto();
                layout.flexDirection(FlexDirection.COLUMN);
                layout.gapAll(1);
            }));
            this.refresh();
        }

        @Override
        public void screenTick() {
            super.screenTick();
            final List<BudgetLine> budget = this.blockEntity.estimatedBudget();
            if(lastRefreshTick > 0){
                lastRefreshTick--;
            }
            if (budget.size() != this.lastBudgetSize || lastRefreshTick == 0) {
                lastRefreshTick = 10;
                this.lastBudgetSize = budget.size();
                this.refresh();
            }
        }

        private void refresh() {
            clearAllScrollViewChildren();
            final List<BudgetLine> original = this.blockEntity.estimatedBudget();
            this.lastBudgetSize = original.size();

            if (original.isEmpty()) {
                final Label empty = emptyLabel();
                addScrollViewChild(empty);
                return;
            }

            // Sort: unsatisfied first, satisfied at bottom
            final List<BudgetLine> sorted = new ArrayList<>(original);
            sorted.sort(Comparator.comparing(BudgetLine::satisfied));

            for (final BudgetLine line : sorted) {
                addScrollViewChild(budgetRow(line));
            }
        }

        private @NotNull Label emptyLabel() {
            final Label empty = new Label();
            empty.setText(Component.translatable(
                    "sable_schematic_api.blueprint_cannon.ui.budget_empty"));
            empty.textStyle(style -> style
                    .fontSize(7)
                    .textColor(MUTED_TEXT_COLOR)
                    .textShadow(false));
            empty.layout(layout -> {
                layout.widthPercent(100);
                layout.height(ROW_HEIGHT);
            });
            return empty;
        }

        private UIElement budgetRow(final BudgetLine line) {
            final UIElement row = new UIElement()
                    .layout(layout -> {
                        layout.widthPercent(100);
                        layout.height(ROW_HEIGHT);
                        layout.flexDirection(FlexDirection.ROW);
                        layout.alignItems(AlignItems.STRETCH);
                        layout.gapAll(2);
                    });

            final UIElement iconAndName = new UIElement()
                .layout(layout -> {
                    layout.flex(1);
                    layout.height(ROW_HEIGHT);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.CENTER);
                    layout.gapAll(2);
                });

            // Item icon
            final UIElement icon = new UIElement()
                    .layout(layout -> {
                        layout.width(14);
                        layout.height(14);
                        layout.flexShrink(0);
                    })
                    .style(style -> style.backgroundTexture(
                            new ItemStackTexture(line.item())));

            final boolean satisfied = line.satisfied();
            final int color = satisfied ? 0xFF_55FF55 : TEXT_COLOR;
            final int displayCount = Math.min(line.available(), line.required());
            final String countText = line.unlimited() ? "\u221E/" + line.required() : displayCount + "/" + line.required();
            Component nameText = line.item().getHoverName();

            Label nameLabel = createLabel(nameText, color);

            // countLabel needs an explicit width; adaptiveWidth is unreliable in flex containers
            final Label countLabel = new Label();
            final int countWidth = Math.min(countText.length() * 5 + 8, line.unlimited() ? 42 : 32);
            countLabel.setText(Component.literal(countText));
            countLabel.textStyle(style -> style
                    .fontSize(7)
                    .textColor(color)
                    .textShadow(false)
                    .textWrap(TextWrap.NONE)
                    .textAlignVertical(Vertical.CENTER)
                    .textAlignHorizontal(Horizontal.RIGHT));
            countLabel.layout(layout -> {
                layout.width(countWidth);
                layout.height(ROW_HEIGHT);
                layout.flexShrink(0);
            });
            // nameLabel already has flex(1) + HOVER_ROLL from createLabel
            // Item name + count
            iconAndName.addChildren(icon ,nameLabel);
            row.addChildren(iconAndName, countLabel);
            return row;
        }
    }

    private static Label createLabel(Component text, int color){
        final Label label = new Label();
        label.setText(text);
        label.textStyle(style -> style
            .adaptiveWidth(false)
            .adaptiveHeight(false)
            .textWrap(TextWrap.HOVER_ROLL)
            .rollSpeed(1.0F)
            .textColor(color)
            .textShadow(false)
            .fontSize(8)
            .textAlignHorizontal(Horizontal.LEFT)
            .textAlignVertical(Vertical.CENTER)
        );
        label.style(style -> style.overflowVisible(false));
        label.layout(layout -> {
            layout.flex(1);
            layout.height(ROW_HEIGHT);
        });
        return label;
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
