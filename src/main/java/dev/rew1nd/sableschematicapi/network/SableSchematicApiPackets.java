package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.tool.SableSchematicApiItems;
import dev.rew1nd.sableschematicapi.tool.client.BlueprintToolServerSubLevelAction;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
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
    public static final long MAX_ACTION_NBT_BYTES = MAX_BLUEPRINT_BYTES + 64L * 1024L;
    static final double MAX_LOAD_DISTANCE_SQUARED = 96.0 * 96.0;

    private SableSchematicApiPackets() {
    }

    public static void register(final IEventBus modEventBus) {
        modEventBus.addListener(SableSchematicApiPackets::registerPayloads);
    }

    public static void sendSaveRequest(final String name, final Vec3 start, final Vec3 end) {
        final CompoundTag data = new CompoundTag();
        data.putString("name", name == null ? "" : name);
        data.put("start", BlueprintToolServerActions.writeVec3(start));
        data.put("end", BlueprintToolServerActions.writeVec3(end));
        sendAction(BlueprintToolServerActions.SAVE_SELECTION, data);
    }

    public static void sendLoadRequest(final String name, final Vec3 origin, final byte[] data) {
        final CompoundTag tag = new CompoundTag();
        tag.putString("name", name == null ? "" : name);
        tag.put("origin", BlueprintToolServerActions.writeVec3(origin));
        tag.putByteArray("data", data == null ? new byte[0] : data);
        sendAction(BlueprintToolServerActions.LOAD_BLUEPRINT, tag);
    }

    public static void sendDeleteRequest() {
        sendAction(BlueprintToolServerActions.DELETE_LOOKED_SUBLEVEL, new CompoundTag());
    }

    public static void sendSubLevelRefreshRequest() {
        sendAction(BlueprintToolServerActions.SUBLEVEL_REFRESH, new CompoundTag());
    }

    public static void sendSubLevelAction(final BlueprintToolServerSubLevelAction action,
                                          final java.util.UUID uuid,
                                          final String name) {
        if (uuid == null) {
            return;
        }

        final CompoundTag data = new CompoundTag();
        data.putUUID("uuid", uuid);
        if (name != null) {
            data.putString("name", name);
        }

        final net.minecraft.resources.ResourceLocation actionId = switch (action) {
            case TP_PLAYER -> BlueprintToolServerActions.SUBLEVEL_TP_PLAYER;
            case BRING -> BlueprintToolServerActions.SUBLEVEL_BRING;
            case TOGGLE_STATIC -> BlueprintToolServerActions.SUBLEVEL_TOGGLE_STATIC;
            case RENAME -> BlueprintToolServerActions.SUBLEVEL_RENAME;
        };
        sendAction(actionId, data);
    }

    public static void sendAction(final net.minecraft.resources.ResourceLocation action, final CompoundTag data) {
        PacketDistributor.sendToServer(new BlueprintToolActionPayload(action, data));
    }

    private static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        BlueprintToolServerActions.registerDefaults();
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(BlueprintToolActionPayload.TYPE, BlueprintToolActionPayload.STREAM_CODEC, SableSchematicApiPackets::handleActionRequest);
        registrar.playToClient(BlueprintToolSaveResultPayload.TYPE, BlueprintToolSaveResultPayload.STREAM_CODEC, SableSchematicApiPackets::handleSaveResult);
        registrar.playToClient(BlueprintToolSubLevelListPayload.TYPE, BlueprintToolSubLevelListPayload.STREAM_CODEC, SableSchematicApiPackets::handleSubLevelList);
    }

    private static void handleActionRequest(final BlueprintToolActionPayload payload, final IPayloadContext context) {
        if (!(context.player() instanceof final ServerPlayer player)) {
            return;
        }

        BlueprintToolServerActions.handle(payload.action(), player, payload.data());
    }

    private static void handleSaveResult(final BlueprintToolSaveResultPayload payload, final IPayloadContext context) {
        BlueprintToolClientPacketHandler.handleSaveResult(payload);
    }

    private static void handleSubLevelList(final BlueprintToolSubLevelListPayload payload, final IPayloadContext context) {
        BlueprintToolClientPacketHandler.handleSubLevelList(payload);
    }

    static void sendSaveResult(final ServerPlayer player,
                               final String name,
                               final boolean success,
                               final String message,
                               final byte[] data) {
        PacketDistributor.sendToPlayer(player, new BlueprintToolSaveResultPayload(name, success, message, data));
    }

    static void sendSubLevelList(final ServerPlayer player, final CompoundTag data) {
        PacketDistributor.sendToPlayer(player, new BlueprintToolSubLevelListPayload(data));
    }

    static boolean canUseBlueprintTool(final ServerPlayer player) {
        return player.hasPermissions(2) && holdingBlueprintTool(player);
    }

    private static boolean holdingBlueprintTool(final Player player) {
        final ItemStack main = player.getMainHandItem();
        final ItemStack off = player.getOffhandItem();
        return main.is(SableSchematicApiItems.BLUEPRINT_TOOL.get()) || off.is(SableSchematicApiItems.BLUEPRINT_TOOL.get());
    }

    static void notify(final ServerPlayer player, final Component message, final ChatFormatting color) {
        player.displayClientMessage(message.copy().withStyle(color), true);
    }

    static Component tr(final String key, final Object... args) {
        return Component.translatable(KEY_PREFIX + key, args);
    }
}
