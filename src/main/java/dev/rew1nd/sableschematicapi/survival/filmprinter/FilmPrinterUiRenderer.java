package dev.rew1nd.sableschematicapi.survival.filmprinter;

import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
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
import dev.rew1nd.sableschematicapi.network.SableSchematicApiPackets;
import dev.rew1nd.sableschematicapi.survival.BudgetLine;
import dev.rew1nd.sableschematicapi.survival.SurvivalMachineUiKit;
import dev.rew1nd.sableschematicapi.survival.client.ui.BlueprintLibraryUiKit;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FilmPrinterUiRenderer {
    private static final float LEFT_WIDTH = 160;
    private static final float RIGHT_WIDTH = 204;
    private static final float ROOT_HEIGHT = 235;
    private static final float MATERIAL_LIST_HEIGHT = 199;

    private FilmPrinterUiRenderer() {
    }

    public static ModularUI render(final FilmPrinterBlockEntity printer, final Player player) {
        final UIElement root = new UIElement()
                .setId("film_printer_root")
                .addClass(BlueprintLibraryUiKit.ROOT_CLASS)
                .addLocalStylesheet(BlueprintLibraryUiKit.LSS)
                .layout(layout -> {
                    layout.width(LEFT_WIDTH + RIGHT_WIDTH + SurvivalMachineUiKit.GAP + 12);
                    layout.height(ROOT_HEIGHT);
                    layout.paddingAll(6);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.STRETCH);
                    layout.gapAll(SurvivalMachineUiKit.GAP);
                });

        final UIElement confirmation = refundConfirmation(printer);
        root.addChildren(
                materialPanel(printer),
                rightPanel(printer, confirmation),
                confirmation
        );
        return ModularUI.of(
                UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.ORE_MERGED))),
                player
        );
    }

    private static UIElement materialPanel(final FilmPrinterBlockEntity printer) {
        final UIElement panel = new UIElement()
                .layout(layout -> {
                    layout.width(LEFT_WIDTH);
                    layout.heightPercent(100);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(SurvivalMachineUiKit.GAP);
                });
        final UIElement header = SurvivalMachineUiKit.row("film_printer_material_header");
        final Label title = SurvivalMachineUiKit.dynamicLabel(
                () -> Component.translatable(
                        printer.phase() == FilmPrinterPhase.REFUNDING
                                ? "sable_schematic_api.film_printer.ui.refund_queue"
                                : "block.sable_schematic_api.blueprint_budget"
                ),
                SurvivalMachineUiKit.TEXT_COLOR
        );
        title.layout(layout -> {
            layout.flex(1);
            layout.height(SurvivalMachineUiKit.ROW_HEIGHT);
        });
        final ItemSlot clipboard = machineSlot(
                printer,
                FilmPrinterBlockEntity.CLIPBOARD_SLOT,
                70,
                true
        );
        final Button write = BlueprintLibraryUiKit.iconButton(
                Icons.SAVE,
                Component.translatable("sable_schematic_api.film_printer.ui.write_clipboard")
        );
        write.setOnServerClick(event -> printer.writeBudgetToClipboard());
        final Button refresh = BlueprintLibraryUiKit.iconButton(
                Icons.REPLAY,
                Component.translatable("sable_schematic_api.film_printer.ui.refresh")
        );
        refresh.setOnClick(event -> SableSchematicApiPackets.sendFilmPrinterBudgetCheck(printer, true));
        header.addChildren(title, clipboard, write, refresh);

        final UIElement listFrame = new UIElement()
                .addClass(BlueprintLibraryUiKit.DETAILS_CLASS)
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.flex(1);
                    layout.paddingAll(3);
                });
        final MaterialList list = new MaterialList(printer);
        list.layout(layout -> {
            layout.widthPercent(100);
            layout.height(MATERIAL_LIST_HEIGHT);
        });
        listFrame.addChild(list);
        panel.addChildren(header, listFrame);
        return panel;
    }

    private static UIElement rightPanel(final FilmPrinterBlockEntity printer,
                                        final UIElement confirmation) {
        final UIElement right = new UIElement()
                .layout(layout -> {
                    layout.width(RIGHT_WIDTH);
                    layout.heightPercent(100);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(SurvivalMachineUiKit.GAP);
                });

        final UIElement header = SurvivalMachineUiKit.row("film_printer_header");
        final Label title = SurvivalMachineUiKit.label(
                Component.translatable("block.sable_schematic_api.film_printer"),
                Horizontal.LEFT,
                SurvivalMachineUiKit.TEXT_COLOR
        );
        title.layout(layout -> {
            layout.flex(1);
            layout.height(SurvivalMachineUiKit.ROW_HEIGHT);
        });
        final Button toggle = BlueprintLibraryUiKit.iconButton(
                printer.enabled() ? Icons.PLAY_PAUSE : Icons.PLAY,
                Component.translatable("sable_schematic_api.film_printer.ui.toggle")
        );
        toggle.setOnServerClick(event -> printer.setEnabled(!printer.enabled()));
        final Button refund = BlueprintLibraryUiKit.iconButton(
                Icons.REPLAY,
                Component.translatable("sable_schematic_api.film_printer.ui.refund")
        );
        refund.setOnClick(event -> {
            if (printer.canRequestRefund()) {
                confirmation.setVisible(true);
                confirmation.setDisplay(true);
            }
        });
        header.addChildren(title, toggle, refund);

        final UIElement status = verticalPanel("film_printer_status");
        status.addChildren(
                dynamicLine(printer::statusLine),
                dynamicLine(printer::taskLine),
                dynamicLine(printer::hashLine),
                dynamicLine(printer::progressLine),
                dynamicLine(printer::nextLine)
        );

        final UIElement slots = verticalPanel("film_printer_slots");
        final UIElement slotRow = SurvivalMachineUiKit.row("film_printer_slot_row");
        slotRow.layout(layout -> layout.height(SurvivalMachineUiKit.SLOT_SIZE));
        final ItemSlot manuscript = machineSlot(printer, FilmPrinterBlockEntity.MANUSCRIPT_SLOT, 100, true);
        final ItemSlot input = machineSlot(printer, FilmPrinterBlockEntity.INPUT_FILM_SLOT, 90, true);
        final Label arrow = SurvivalMachineUiKit.label(
                Component.literal("\u2192"),
                Horizontal.CENTER,
                SurvivalMachineUiKit.TEXT_COLOR
        );
        arrow.layout(layout -> {
            layout.width(16);
            layout.height(SurvivalMachineUiKit.SLOT_SIZE);
        });
        final ItemSlot output = machineSlot(printer, FilmPrinterBlockEntity.OUTPUT_FILM_SLOT, 80, false);
        slotRow.addChildren(manuscript, input, arrow, output);
        slots.addChildren(slotRow);

        final UIElement playerInventory = verticalPanel("film_printer_player_inventory_panel");
        final InventorySlots inventory = new InventorySlots();
        inventory.apply(slot -> slot.slotStyle(style -> style
                .slotOverlay(ItemSlot.ITEM_SLOT_TEXTURE.copy())
                .showSlotOverlayOnlyEmpty(true)
                .quickMovePriority(0)
                .acceptQuickMove(true)
                .isPlayerSlot(true)));
        inventory.layout(layout -> {
            layout.width(SurvivalMachineUiKit.SLOT_SIZE * 9);
            layout.height(SurvivalMachineUiKit.SLOT_SIZE * 4 + 5);
            layout.alignSelf(AlignItems.CENTER);
        });
        playerInventory.addChildren(inventory);

        right.addChildren(header, status, slots, playerInventory);
        return right;
    }

    private static UIElement refundConfirmation(final FilmPrinterBlockEntity printer) {
        final UIElement dialog = new UIElement()
                .setId("film_printer_refund_confirm")
                .addClass(BlueprintLibraryUiKit.OVERLAY_CLASS)
                .layout(layout -> {
                    layout.positionType(TaffyPosition.ABSOLUTE);
                    layout.left(94);
                    layout.top(62);
                    layout.width(190);
                    layout.height(30);
                    layout.paddingAll(6);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.CENTER);
                    layout.gapAll(SurvivalMachineUiKit.GAP);
                });
        final Label warning = SurvivalMachineUiKit.label(
                Component.translatable("sable_schematic_api.film_printer.ui.refund_confirm"),
                Horizontal.LEFT,
                SurvivalMachineUiKit.TEXT_COLOR
        );
        warning.layout(layout -> {
            layout.flex(1);
            layout.height(SurvivalMachineUiKit.ROW_HEIGHT);
        });
        final Button confirm = BlueprintLibraryUiKit.iconButton(
                Icons.CHECK,
                Component.translatable("sable_schematic_api.film_printer.ui.confirm")
        );
        confirm.setOnServerClick(event -> printer.requestRefund());
        confirm.setOnClick(event -> {
            dialog.setVisible(false);
            dialog.setDisplay(false);
        });
        final Button cancel = BlueprintLibraryUiKit.iconButton(
                Icons.CLOSE,
                Component.translatable("sable_schematic_api.film_printer.ui.cancel")
        );
        cancel.setOnClick(event -> {
            dialog.setVisible(false);
            dialog.setDisplay(false);
        });
        dialog.addChildren(warning, confirm, cancel);
        dialog.setVisible(false);
        dialog.setDisplay(false);
        return dialog;
    }

    private static UIElement verticalPanel(final String id) {
        return new UIElement()
                .setId(id)
                .addClass(BlueprintLibraryUiKit.DETAILS_CLASS)
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.heightAuto();
                    layout.paddingAll(4);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(2);
                });
    }

    private static Label dynamicLine(final java.util.function.Supplier<Component> supplier) {
        final Label label = SurvivalMachineUiKit.dynamicLabel(
                supplier,
                SurvivalMachineUiKit.MUTED_TEXT_COLOR
        );
        label.addClass(BlueprintLibraryUiKit.STATE_CLASS);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(SurvivalMachineUiKit.ROW_HEIGHT);
        });
        return label;
    }

    private static ItemSlot machineSlot(final FilmPrinterBlockEntity printer,
                                        final int slot,
                                        final int priority,
                                        final boolean canPlace) {
        final ItemHandlerSlot handler = new ItemHandlerSlot(printer.inventory(), slot)
                .setCanPlace(stack -> canPlace && printer.inventory().isItemValid(slot, stack));
        final ItemSlot itemSlot = new ItemSlot(handler);
        itemSlot.slotStyle(style -> style
                .quickMovePriority(priority)
                .acceptQuickMove(true)
                .isPlayerSlot(false));
        itemSlot.layout(layout -> {
            layout.width(SurvivalMachineUiKit.SLOT_SIZE);
            layout.height(SurvivalMachineUiKit.SLOT_SIZE);
            layout.flexShrink(0);
        });
        return itemSlot;
    }

    private static final class MaterialList extends ScrollerView {
        private final FilmPrinterBlockEntity printer;
        private List<BudgetLine> previousBudget = List.of();
        private List<ItemStack> previousRefund = List.of();
        private FilmPrinterPhase previousPhase;
        private int snapshotCheckTicks;

        private MaterialList(final FilmPrinterBlockEntity printer) {
            this.printer = printer;
            this.viewContainer(container -> container.layout(layout -> {
                layout.width(LEFT_WIDTH - 16);
                layout.heightAuto();
                layout.flexDirection(FlexDirection.COLUMN);
                layout.gapAll(1);
            }));
            this.refresh();
            SableSchematicApiPackets.sendFilmPrinterBudgetCheck(this.printer, false);
        }

        @Override
        public void screenTick() {
            super.screenTick();
            if (++this.snapshotCheckTicks >= 20) {
                this.snapshotCheckTicks = 0;
                SableSchematicApiPackets.sendFilmPrinterBudgetCheck(this.printer, false);
            }
            final List<BudgetLine> budget = this.printer.nextBudget();
            final List<ItemStack> refund = this.printer.refundQueue();
            if (this.previousPhase != this.printer.phase()
                    || !sameBudget(this.previousBudget, budget)
                    || !sameStacks(this.previousRefund, refund)) {
                this.refresh();
            }
        }

        private void refresh() {
            this.clearAllScrollViewChildren();
            this.previousPhase = this.printer.phase();
            this.previousBudget = this.printer.nextBudget();
            this.previousRefund = this.printer.refundQueue();
            if (this.previousPhase == FilmPrinterPhase.REFUNDING) {
                if (this.previousRefund.isEmpty()) {
                    this.addScrollViewChild(emptyLabel("sable_schematic_api.film_printer.ui.refund_empty"));
                } else {
                    for (final ItemStack item : this.previousRefund) {
                        this.addScrollViewChild(itemRow(item, "x" + item.getCount(), false));
                    }
                }
                return;
            }
            if (this.previousBudget.isEmpty()) {
                this.addScrollViewChild(emptyLabel("sable_schematic_api.blueprint_cannon.ui.budget_empty"));
                return;
            }
            final List<BudgetLine> sorted = new ArrayList<>(this.previousBudget);
            sorted.sort(Comparator.comparing(BudgetLine::satisfied));
            for (final BudgetLine line : sorted) {
                final int shown = Math.min(line.available(), line.required());
                final String count = line.unlimited()
                        ? "\u221e/" + line.required()
                        : shown + "/" + line.required();
                this.addScrollViewChild(itemRow(line.item(), count, line.satisfied()));
            }
        }

        private static Label emptyLabel(final String key) {
            final Label label = SurvivalMachineUiKit.label(
                    Component.translatable(key),
                    Horizontal.LEFT,
                    SurvivalMachineUiKit.MUTED_TEXT_COLOR
            );
            label.layout(layout -> {
                layout.widthPercent(100);
                layout.height(SurvivalMachineUiKit.ROW_HEIGHT);
            });
            return label;
        }

        private static UIElement itemRow(final ItemStack item,
                                         final String count,
                                         final boolean satisfied) {
            final UIElement row = SurvivalMachineUiKit.row("film_printer_material_line");
            row.addClass(BlueprintLibraryUiKit.TILE_CLASS);
            final UIElement icon = new UIElement()
                    .layout(layout -> {
                        layout.width(14);
                        layout.height(14);
                        layout.flexShrink(0);
                    })
                    .style(style -> style.backgroundTexture(new ItemStackTexture(item)));
            final int color = satisfied ? 0xFF55FF55 : SurvivalMachineUiKit.TEXT_COLOR;
            final Label name = SurvivalMachineUiKit.label(item.getHoverName(), Horizontal.LEFT, color);
            name.layout(layout -> {
                layout.flex(1);
                layout.height(SurvivalMachineUiKit.ROW_HEIGHT);
            });
            final Label amount = SurvivalMachineUiKit.label(
                    Component.literal(count),
                    Horizontal.RIGHT,
                    color
            );
            amount.textStyle(style -> style.textWrap(TextWrap.NONE).textAlignVertical(Vertical.CENTER));
            amount.layout(layout -> {
                layout.width(38);
                layout.height(SurvivalMachineUiKit.ROW_HEIGHT);
                layout.flexShrink(0);
            });
            row.addChildren(icon, name, amount);
            return row;
        }

        private static boolean sameBudget(final List<BudgetLine> left, final List<BudgetLine> right) {
            if (left.size() != right.size()) {
                return false;
            }
            for (int i = 0; i < left.size(); i++) {
                final BudgetLine a = left.get(i);
                final BudgetLine b = right.get(i);
                if (a.required() != b.required()
                        || a.available() != b.available()
                        || a.unlimited() != b.unlimited()
                        || !ItemStack.isSameItemSameComponents(a.item(), b.item())) {
                    return false;
                }
            }
            return true;
        }

        private static boolean sameStacks(final List<ItemStack> left, final List<ItemStack> right) {
            if (left.size() != right.size()) {
                return false;
            }
            for (int i = 0; i < left.size(); i++) {
                if (left.get(i).getCount() != right.get(i).getCount()
                        || !ItemStack.isSameItemSameComponents(left.get(i), right.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }
}
