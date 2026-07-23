package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;
import java.util.List;

/** Server response for a camera action, optionally including a blueprint download. */
public record CameraResultPayload(String action,
                                  boolean success,
                                  String reason,
                                  String name,
                                  UUID captureId,
                                  int bodyCount,
                                  List<UUID> bodyIds,
                                  byte[] data) implements CustomPacketPayload {
    private static final int MAX_TEXT = 256;
    public static final Type<CameraResultPayload> TYPE = new Type<>(SableSchematicApi.id("camera_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraResultPayload> STREAM_CODEC = StreamCodec.ofMember(
            CameraResultPayload::write,
            CameraResultPayload::read
    );

    public CameraResultPayload {
        action = action == null ? "" : action;
        reason = reason == null ? "" : reason;
        name = name == null ? "" : name;
        bodyIds = bodyIds == null ? List.of() : List.copyOf(bodyIds);
        data = data == null ? new byte[0] : data;
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.action, MAX_TEXT);
        buffer.writeBoolean(this.success);
        buffer.writeUtf(this.reason, MAX_TEXT);
        buffer.writeUtf(this.name, 128);
        buffer.writeBoolean(this.captureId != null);
        if (this.captureId != null) {
            buffer.writeUUID(this.captureId);
        }
        buffer.writeVarInt(this.bodyCount);
        buffer.writeVarInt(this.bodyIds.size());
        for (final UUID bodyId : this.bodyIds) {
            buffer.writeUUID(bodyId);
        }
        buffer.writeByteArray(this.data);
    }

    private static CameraResultPayload read(final RegistryFriendlyByteBuf buffer) {
        final String action = buffer.readUtf(MAX_TEXT);
        final boolean success = buffer.readBoolean();
        final String reason = buffer.readUtf(MAX_TEXT);
        final String name = buffer.readUtf(128);
        final UUID id = buffer.readBoolean() ? buffer.readUUID() : null;
        final int count = buffer.readVarInt();
        final int bodyIdCount = buffer.readVarInt();
        if (bodyIdCount < 0 || bodyIdCount > 128) {
            throw new IllegalArgumentException("Invalid camera body ID count: " + bodyIdCount);
        }
        final java.util.ArrayList<UUID> bodyIds = new java.util.ArrayList<>(bodyIdCount);
        for (int index = 0; index < bodyIdCount; index++) {
            bodyIds.add(buffer.readUUID());
        }
        final byte[] data = buffer.readByteArray(SableSchematicApiPackets.MAX_BLUEPRINT_BYTES);
        return new CameraResultPayload(action, success, reason, name, id, count, bodyIds, data);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
