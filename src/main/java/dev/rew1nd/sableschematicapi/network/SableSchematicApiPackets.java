package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolFile;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolResult;
import dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolService;
import dev.rew1nd.sableschematicapi.tool.SableSchematicApiItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SableSchematicApiPackets {
    private static final String KEY_PREFIX = "sable_schematic_api.blueprint_tool.";
    public static final int MAX_BLUEPRINT_BYTES = 16 * 1024 * 1024;
    private static final double MAX_LOAD_DISTANCE_SQUARED = 96.0 * 96.0;

    private SableSchematicApiPackets() {
    }

    public static void register(final IEventBus modEventBus) {
        modEventBus.addListener(SableSchematicApiPackets::registerPayloads);
    }

    public static void sendSaveRequest(final String name, final Vec3 start, final Vec3 end) {
        PacketDistributor.sendToServer(new BlueprintToolSaveRequestPayload(name, start, end));
    }

    public static void sendLoadRequest(final String name, final Vec3 origin, final byte[] data) {
        PacketDistributor.sendToServer(new BlueprintToolLoadRequestPayload(name, origin, data));
    }

    private static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(BlueprintToolSaveRequestPayload.TYPE, BlueprintToolSaveRequestPayload.STREAM_CODEC, SableSchematicApiPackets::handleSaveRequest);
        registrar.playToServer(BlueprintToolLoadRequestPayload.TYPE, BlueprintToolLoadRequestPayload.STREAM_CODEC, SableSchematicApiPackets::handleLoadRequest);
        registrar.playToClient(BlueprintToolSaveResultPayload.TYPE, BlueprintToolSaveResultPayload.STREAM_CODEC, SableSchematicApiPackets::handleSaveResult);
    }

    private static void handleSaveRequest(final BlueprintToolSaveRequestPayload payload, final IPayloadContext context) {
        if (!(context.player() instanceof final ServerPlayer player)) {
            return;
        }

        if (!canUseBlueprintTool(player)) {
            sendSaveResult(player, payload.name(), false, "op_required", new byte[0]);
            return;
        }

        final BlueprintToolFile file = BlueprintToolService.saveSelectionToBytes(player.serverLevel(), payload.start(), payload.end(), payload.name());
        sendSaveResult(player, payload.name(), file.success(), file.result().message(), file.data());
    }

    private static void handleLoadRequest(final BlueprintToolLoadRequestPayload payload, final IPayloadContext context) {
        if (!(context.player() instanceof final ServerPlayer player)) {
            return;
        }

        if (!canUseBlueprintTool(player)) {
            notify(player, tr("status.op_required"), ChatFormatting.RED);
            return;
        }

        if (payload.data().length == 0 || payload.data().length > MAX_BLUEPRINT_BYTES) {
            notify(player, tr("status.invalid_upload"), ChatFormatting.RED);
            return;
        }

        if (player.position().distanceToSqr(payload.origin()) > MAX_LOAD_DISTANCE_SQUARED) {
            notify(player, tr("status.too_far"), ChatFormatting.RED);
            return;
        }

        final BlueprintToolResult result = BlueprintToolService.loadBytes(player.serverLevel(), payload.origin(), payload.data(), payload.name());
        notify(player,
                result.success() ? tr("status.placed", payload.name()) : tr("status.place_failed"),
                result.success() ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static void handleSaveResult(final BlueprintToolSaveResultPayload payload, final IPayloadContext context) {
        BlueprintToolClientPacketHandler.handleSaveResult(payload);
    }

    private static void sendSaveResult(final ServerPlayer player,
                                       final String name,
                                       final boolean success,
                                       final String message,
                                       final byte[] data) {
        PacketDistributor.sendToPlayer(player, new BlueprintToolSaveResultPayload(name, success, message, data));
    }

    private static boolean canUseBlueprintTool(final ServerPlayer player) {
        return player.hasPermissions(2) && holdingBlueprintTool(player);
    }

    private static boolean holdingBlueprintTool(final Player player) {
        final ItemStack main = player.getMainHandItem();
        final ItemStack off = player.getOffhandItem();
        return main.is(SableSchematicApiItems.BLUEPRINT_TOOL.get()) || off.is(SableSchematicApiItems.BLUEPRINT_TOOL.get());
    }

    private static void notify(final ServerPlayer player, final Component message, final ChatFormatting color) {
        player.displayClientMessage(message.copy().withStyle(color), true);
    }

    private static Component tr(final String key, final Object... args) {
        return Component.translatable(KEY_PREFIX + key, args);
    }
}
