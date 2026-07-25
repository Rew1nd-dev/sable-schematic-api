package dev.rew1nd.sableschematicapi.survival.honeywelder.client;

import dev.rew1nd.sableschematicapi.api.honeywelder.HoneyWelderCancelReason;
import dev.rew1nd.sableschematicapi.api.honeywelder.client.HoneyWelderClientConsumer;
import dev.rew1nd.sableschematicapi.api.honeywelder.client.HoneyWelderClientConsumers;
import dev.rew1nd.sableschematicapi.network.HoneyWelderControlPayload;
import dev.rew1nd.sableschematicapi.network.HoneyWelderSessionPayload;
import dev.rew1nd.sableschematicapi.survival.honeywelder.HoneyWelderItem;
import dev.rew1nd.sableschematicapi.survival.honeywelder.HoneyWelderState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

public final class HoneyWelderClientSession {
    private static ResourceLocation activeConsumer;
    private static ResourceLocation activeAction;
    private static int activeHotbarSlot = -1;

    private HoneyWelderClientSession() {
    }

    public static void selectConsumer(final ResourceLocation consumer) {
        final Player player = Minecraft.getInstance().player;
        if (consumer == null
                || player == null
                || !HoneyWelderItem.is(player.getMainHandItem())
                || active()) {
            return;
        }
        HoneyWelderState.write(
                player.getMainHandItem(),
                HoneyWelderState.read(player.getMainHandItem()).withSelectedConsumer(consumer)
        );
        PacketDistributor.sendToServer(new HoneyWelderControlPayload(
                HoneyWelderControlPayload.Command.SELECT_CONSUMER,
                consumer
        ));
    }

    public static void requestStart(final ResourceLocation action) {
        if (action == null || active()) {
            return;
        }
        PacketDistributor.sendToServer(new HoneyWelderControlPayload(
                HoneyWelderControlPayload.Command.START,
                action
        ));
    }

    public static void requestCancel() {
        if (!active()) {
            return;
        }
        endLocal(HoneyWelderCancelReason.USER, false, "");
        PacketDistributor.sendToServer(new HoneyWelderControlPayload(
                HoneyWelderControlPayload.Command.CANCEL,
                HoneyWelderControlPayload.NONE
        ));
    }

    public static void handle(final HoneyWelderSessionPayload payload) {
        final Player player = Minecraft.getInstance().player;
        if (player == null) {
            clear();
            return;
        }
        switch (payload.event()) {
            case STARTED -> {
                activeConsumer = payload.consumer();
                activeAction = payload.action();
                activeHotbarSlot = player.getInventory().selected;
                final HoneyWelderClientConsumer consumer =
                        HoneyWelderClientConsumers.find(activeConsumer).orElse(null);
                if (consumer == null) {
                    notify(player, "consumer_missing", ChatFormatting.RED);
                    requestCancel();
                    return;
                }
                consumer.sessionStarted(player, activeAction, payload.fuel());
            }
            case REJECTED -> {
                clear();
                notify(player, payload.message().isBlank() ? "consumer_rejected" : payload.message(), ChatFormatting.RED);
            }
            case CANCELLED -> endLocal(payload.reason(), false, payload.message());
            case FINISHED -> endLocal(HoneyWelderCancelReason.USER, true, payload.message());
        }
    }

    public static boolean active() {
        return activeConsumer != null;
    }

    public static int activeHotbarSlot() {
        return activeHotbarSlot;
    }

    public static Optional<HoneyWelderClientConsumer> activeProvider() {
        return HoneyWelderClientConsumers.find(activeConsumer);
    }

    public static Optional<HoneyWelderClientConsumer> selectedProvider(final Player player) {
        if (player == null || !HoneyWelderItem.is(player.getMainHandItem())) {
            return Optional.empty();
        }
        return HoneyWelderState.read(player.getMainHandItem())
                .selectedConsumer()
                .flatMap(HoneyWelderClientConsumers::find);
    }

    public static void cancelForItemChange() {
        if (!active()) {
            return;
        }
        final ResourceLocation consumer = activeConsumer;
        endLocal(HoneyWelderCancelReason.ITEM_CHANGED, false, "");
        PacketDistributor.sendToServer(new HoneyWelderControlPayload(
                HoneyWelderControlPayload.Command.CANCEL,
                consumer == null ? HoneyWelderControlPayload.NONE : consumer
        ));
    }

    private static void endLocal(final HoneyWelderCancelReason reason,
                                 final boolean finished,
                                 final String message) {
        final Player player = Minecraft.getInstance().player;
        final HoneyWelderClientConsumer consumer =
                HoneyWelderClientConsumers.find(activeConsumer).orElse(null);
        clear();
        if (player != null && consumer != null) {
            consumer.sessionEnded(player, reason, finished, message == null ? "" : message);
        }
    }

    private static void clear() {
        activeConsumer = null;
        activeAction = null;
        activeHotbarSlot = -1;
    }

    private static void notify(final Player player, final String reason, final ChatFormatting formatting) {
        final String safeReason = reason == null || reason.isBlank() ? "consumer_rejected" : reason;
        player.displayClientMessage(Component.translatable(
                "message.sable_schematic_api.honey_welder." + safeReason
        ).withStyle(formatting), true);
    }
}
