package dev.rew1nd.sableschematicapi.survival.projector;

import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.rew1nd.sableschematicapi.survival.SurvivalMachineUiKit;
import dev.rew1nd.sableschematicapi.survival.client.ui.BlueprintLibraryUiKit;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public final class ProjectorUiRenderer {
    private ProjectorUiRenderer() {
    }

    public static ModularUI render(final ProjectorBlockEntity projector, final Player player) {
        final UIElement root = new UIElement()
                .setId("projector_root")
                .addClass(BlueprintLibraryUiKit.ROOT_CLASS)
                .addLocalStylesheet(BlueprintLibraryUiKit.LSS)
                .layout(layout -> {
                    layout.width(236);
                    layout.height(194);
                    layout.paddingAll(6);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(SurvivalMachineUiKit.GAP);
                });

        final UIElement details = new UIElement()
                .addClass(BlueprintLibraryUiKit.DETAILS_CLASS)
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.flex(1);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(2);
                });
        details.addChildren(
                line(projector::filmLine),
                line(projector::bodyLine),
                line(projector::statusLine),
                line(projector::progressLine)
        );

        final UIElement playerInventoryPanel = new UIElement()
                .addClass(BlueprintLibraryUiKit.DETAILS_CLASS)
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.height(SurvivalMachineUiKit.SLOT_SIZE * 4 + 13);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.alignItems(AlignItems.CENTER);
                    layout.flexShrink(0);
                });
        final InventorySlots playerInventory = new InventorySlots();
        playerInventory.apply(slot -> slot.slotStyle(style -> style
                .slotOverlay(ItemSlot.ITEM_SLOT_TEXTURE.copy())
                .showSlotOverlayOnlyEmpty(true)
                .quickMovePriority(0)
                .acceptQuickMove(true)
                .isPlayerSlot(true)));
        playerInventory.layout(layout -> {
            layout.width(SurvivalMachineUiKit.SLOT_SIZE * 9);
            layout.height(SurvivalMachineUiKit.SLOT_SIZE * 4 + 5);
            layout.alignSelf(AlignItems.CENTER);
        });
        playerInventoryPanel.addChild(playerInventory);

        final UIElement controls = SurvivalMachineUiKit.row("projector_controls");
        controls.layout(layout -> layout.height(SurvivalMachineUiKit.SLOT_SIZE));
        final ItemHandlerSlot handler = new ItemHandlerSlot(projector.inventory(), 0)
                .setCanPlace(stack -> projector.inventory().isItemValid(0, stack));
        final ItemSlot slot = new ItemSlot(handler);
        slot.slotStyle(style -> style.quickMovePriority(100).acceptQuickMove(true).isPlayerSlot(false));
        slot.layout(layout -> {
            layout.width(SurvivalMachineUiKit.SLOT_SIZE);
            layout.height(SurvivalMachineUiKit.SLOT_SIZE);
            layout.flexShrink(0);
        });
        final Button action = new ProjectorActionButton(projector);
        action.layout(layout -> {
            layout.flex(1);
            layout.height(SurvivalMachineUiKit.SLOT_SIZE);
        });
        action.setOnServerClick(event -> {
            if (player instanceof final ServerPlayer serverPlayer) {
                projector.operate(serverPlayer);
            }
        });
        controls.addChildren(slot, action);
        root.addChildren(details, controls, playerInventoryPanel);

        return ModularUI.of(
                UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.ORE_MERGED))),
                player
        );
    }

    private static UIElement line(final java.util.function.Supplier<Component> supplier) {
        final var label = SurvivalMachineUiKit.dynamicLabel(supplier, SurvivalMachineUiKit.TEXT_COLOR);
        label.addClass(BlueprintLibraryUiKit.STATE_CLASS);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(SurvivalMachineUiKit.ROW_HEIGHT);
        });
        return label;
    }

    private static final class ProjectorActionButton extends Button {
        private final ProjectorBlockEntity projector;
        private ProjectorPhase previous;

        private ProjectorActionButton(final ProjectorBlockEntity projector) {
            this.projector = projector;
            this.style(style -> style.tooltips(
                    Component.translatable("sable_schematic_api.projector.ui.operate")));
            this.refresh();
        }

        @Override
        public void screenTick() {
            super.screenTick();
            if (this.previous != this.projector.phase()) {
                this.refresh();
            }
        }

        private void refresh() {
            this.previous = this.projector.phase();
            final String key = this.previous == ProjectorPhase.REVEALING
                    ? "sable_schematic_api.projector.ui.stop"
                    : this.previous.admission()
                    ? "sable_schematic_api.projector.ui.cancel"
                    : "sable_schematic_api.projector.ui.project";
            this.setText(Component.translatable(key));
        }
    }
}
