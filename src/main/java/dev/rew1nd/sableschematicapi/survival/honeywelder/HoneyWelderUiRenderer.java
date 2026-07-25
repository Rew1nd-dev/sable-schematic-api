package dev.rew1nd.sableschematicapi.survival.honeywelder;

import com.lowdragmc.lowdraglib2.gui.factory.HeldItemUIMenuType;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import dev.rew1nd.sableschematicapi.api.honeywelder.HoneyWelderConsumer;
import dev.rew1nd.sableschematicapi.api.honeywelder.HoneyWelderConsumers;
import dev.rew1nd.sableschematicapi.api.honeywelder.client.HoneyWelderClientConsumer;
import dev.rew1nd.sableschematicapi.api.honeywelder.client.HoneyWelderClientConsumers;
import dev.rew1nd.sableschematicapi.survival.SurvivalMachineUiKit;
import dev.rew1nd.sableschematicapi.survival.client.ui.BlueprintLibraryUiKit;
import dev.rew1nd.sableschematicapi.survival.honeywelder.client.HoneyWelderClientSession;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.AlignContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class HoneyWelderUiRenderer {
    private static final float WIDTH = 286;
    private static final float PROVIDER_HEIGHT = 92;

    private HoneyWelderUiRenderer() {
    }

    public static ModularUI render(final HeldItemUIMenuType.HeldItemUIHolder holder) {
        final UIElement root = new UIElement()
                .setId("honey_welder_root")
                .addClass(BlueprintLibraryUiKit.ROOT_CLASS)
                .addLocalStylesheet(BlueprintLibraryUiKit.LSS)
                .layout(layout -> {
                    layout.width(WIDTH);
                    layout.paddingAll(7);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.gapAll(5);
                });

        final UIElement fuelRow = new UIElement()
                .addClass(BlueprintLibraryUiKit.DETAILS_CLASS)
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.height(30);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.CENTER);
                    layout.gapAll(5);
                });
        final ItemHandlerSlot fuelHandlerSlot = new ItemHandlerSlot(new FuelInputHandler(holder), 0)
                .setCanPlace(stack -> stack.is(Items.HONEY_BOTTLE))
                .setCanTake(player -> false);
        final ItemSlot fuelSlot = new ItemSlot(fuelHandlerSlot);
        fuelSlot.slotStyle(style -> style
                .quickMovePriority(100)
                .acceptQuickMove(true)
                .isPlayerSlot(false));
        fuelSlot.layout(layout -> {
            layout.width(SurvivalMachineUiKit.SLOT_SIZE);
            layout.height(SurvivalMachineUiKit.SLOT_SIZE);
            layout.flexShrink(0);
        });

        final UIElement fuelInfo = new UIElement().layout(layout -> {
            layout.flex(1);
            layout.height(22);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(2);
        });
        final var fuelLabel = SurvivalMachineUiKit.dynamicLabel(
                () -> Component.translatable(
                        "gui.sable_schematic_api.honey_welder.fuel",
                        state(holder).fuel(),
                        HoneyWelderConfig.capacity()),
                SurvivalMachineUiKit.TEXT_COLOR);
        fuelLabel.layout(layout -> {
            layout.widthPercent(100);
            layout.height(10);
        });
        final FuelProgressBar fuelBar = new FuelProgressBar(holder);
        fuelBar.layout(layout -> {
            layout.widthPercent(100);
            layout.height(10);
        });
        fuelInfo.addChildren(fuelLabel, fuelBar);
        fuelRow.addChildren(fuelSlot, fuelInfo);

        final UIElement consumerRow = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(18);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(5);
        });
        final var consumerLabel = SurvivalMachineUiKit.label(
                Component.translatable("gui.sable_schematic_api.honey_welder.consumer"),
                com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal.LEFT,
                SurvivalMachineUiKit.TEXT_COLOR);
        consumerLabel.layout(layout -> {
            layout.width(65);
            layout.height(18);
            layout.flexShrink(0);
        });
        final Selector<ResourceLocation> selector = new Selector<>();
        selector.setCandidates(consumerCandidates(state(holder).selectedConsumer()));
        selector.setCandidateUIProvider(UIElementProvider.text(HoneyWelderUiRenderer::consumerName));
        state(holder).selectedConsumer().ifPresent(id -> selector.setSelected(id, false));
        selector.layout(layout -> {
            layout.flex(1);
            layout.height(18);
        });
        consumerRow.addChildren(consumerLabel, selector);

        final UIElement providerPanel = new UIElement()
                .addClass(BlueprintLibraryUiKit.DETAILS_CLASS)
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.height(PROVIDER_HEIGHT);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.justifyContent(AlignContent.CENTER);
                });
        rebuildProviderPanel(providerPanel, holder, state(holder).selectedConsumer().orElse(null));
        selector.setOnValueChanged(selected -> {
            if (selected == null) {
                rebuildProviderPanel(providerPanel, holder, null);
                return;
            }
            HoneyWelderClientSession.selectConsumer(selected);
            rebuildProviderPanel(providerPanel, holder, selected);
        });

        final UIElement playerInventoryPanel = new UIElement()
                .addClass(BlueprintLibraryUiKit.DETAILS_CLASS)
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.height(SurvivalMachineUiKit.SLOT_SIZE * 4 + 13);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.alignItems(AlignItems.CENTER);
                    layout.flexShrink(0);
                });
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
        playerInventoryPanel.addChild(inventory);

        root.addChildren(fuelRow, consumerRow, providerPanel, playerInventoryPanel);
        return ModularUI.of(
                UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.ORE_MERGED))),
                holder.player
        );
    }

    private static void rebuildProviderPanel(final UIElement panel,
                                             final HeldItemUIMenuType.HeldItemUIHolder holder,
                                             final ResourceLocation selected) {
        panel.clearAllChildren();
        if (selected == null) {
            panel.addChild(unavailableLabel("gui.sable_schematic_api.honey_welder.select_consumer"));
            return;
        }
        final HoneyWelderClientConsumer consumer = HoneyWelderClientConsumers.find(selected).orElse(null);
        if (consumer == null) {
            panel.addChild(unavailableLabel("gui.sable_schematic_api.honey_welder.consumer_unavailable"));
            return;
        }
        final UIElement content = consumer.createPanel(holder.player);
        if (content == null) {
            panel.addChild(unavailableLabel("gui.sable_schematic_api.honey_welder.consumer_unavailable"));
            return;
        }
        content.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
        });
        panel.addChild(content);
    }

    private static UIElement unavailableLabel(final String key) {
        final var label = SurvivalMachineUiKit.label(
                Component.translatable(key).withStyle(ChatFormatting.GRAY),
                com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal.CENTER,
                SurvivalMachineUiKit.MUTED_TEXT_COLOR);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(14);
        });
        return label;
    }

    private static HoneyWelderState state(final HeldItemUIMenuType.HeldItemUIHolder holder) {
        return HoneyWelderState.read(heldStack(holder));
    }

    private static ItemStack heldStack(final HeldItemUIMenuType.HeldItemUIHolder holder) {
        final ItemStack mainHand = holder.player.getMainHandItem();
        return HoneyWelderItem.is(mainHand) ? mainHand : holder.itemStack;
    }

    private static List<ResourceLocation> consumerCandidates(final Optional<ResourceLocation> selected) {
        final List<ResourceLocation> result = new ArrayList<>();
        for (final HoneyWelderConsumer consumer : HoneyWelderConsumers.values()) {
            result.add(consumer.id());
        }
        selected.filter(id -> !result.contains(id)).ifPresent(result::add);
        result.sort(Comparator.comparing(ResourceLocation::toString));
        return result;
    }

    private static Component consumerName(final ResourceLocation id) {
        if (id == null) {
            return Component.translatable("tooltip.sable_schematic_api.honey_welder.consumer_none")
                    .withStyle(ChatFormatting.GRAY);
        }
        return HoneyWelderConsumers.find(id)
                .map(HoneyWelderConsumer::label)
                .orElseGet(() -> Component.literal(id.toString()).withStyle(ChatFormatting.RED));
    }

    private static final class FuelProgressBar extends ProgressBar {
        private final HeldItemUIMenuType.HeldItemUIHolder holder;
        private int lastFuel = Integer.MIN_VALUE;
        private int lastCapacity = Integer.MIN_VALUE;

        private FuelProgressBar(final HeldItemUIMenuType.HeldItemUIHolder holder) {
            this.holder = holder;
            refresh();
            label(label -> label.setText(Component.empty()));
        }

        @Override
        public void screenTick() {
            super.screenTick();
            refresh();
        }

        private void refresh() {
            final int capacity = HoneyWelderConfig.capacity();
            final int fuel = state(holder).fuel();
            if (capacity != lastCapacity) {
                setRange(0, capacity);
                lastCapacity = capacity;
            }
            if (fuel != lastFuel) {
                setProgress(fuel);
                lastFuel = fuel;
            }
        }
    }

    private static final class FuelInputHandler extends ItemStackHandler {
        private final HeldItemUIMenuType.HeldItemUIHolder holder;

        private FuelInputHandler(final HeldItemUIMenuType.HeldItemUIHolder holder) {
            super(1);
            this.holder = holder;
        }

        @Override
        public ItemStack getStackInSlot(final int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(final int slot) {
            return 16;
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return slot == 0 && stack.is(Items.HONEY_BOTTLE);
        }

        @Override
        public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
            if (!isItemValid(slot, stack) || stack.isEmpty()) {
                return stack;
            }
            final int perBottle = HoneyWelderConfig.fuelPerBottle();
            final int room = HoneyWelderConfig.capacity() - state(holder).fuel();
            final int accepted = Math.min(stack.getCount(), room / perBottle);
            if (accepted <= 0) {
                return stack;
            }
            final ItemStack remainder = stack.copy();
            remainder.shrink(accepted);
            if (!simulate && holder.player instanceof final ServerPlayer serverPlayer) {
                final HoneyWelderState current = state(holder);
                HoneyWelderState.write(heldStack(holder), current.withFuel(current.fuel() + accepted * perBottle));
                for (int i = 0; i < accepted; i++) {
                    final ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                    if (!serverPlayer.getInventory().add(bottle)) {
                        serverPlayer.drop(bottle, false);
                    }
                }
                serverPlayer.getInventory().setChanged();
            }
            return remainder;
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(final int slot, final ItemStack stack) {
            insertItem(slot, stack, false);
        }
    }
}
