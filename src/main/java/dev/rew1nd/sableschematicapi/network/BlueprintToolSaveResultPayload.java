package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BlueprintToolSaveResultPayload(String name, boolean success, String message, byte[] data) implements CustomPacketPayload {
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_MESSAGE_LENGTH = 1024;
    public static final Type<BlueprintToolSaveResultPayload> TYPE = new Type<>(SableSchematicApi.id("blueprint_tool_save_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintToolSaveResultPayload> STREAM_CODEC = StreamCodec.ofMember(
            BlueprintToolSaveResultPayload::write,
            BlueprintToolSaveResultPayload::read
    );

    public BlueprintToolSaveResultPayload {
        name = name == null ? "" : name;
        message = message == null ? "" : message;
        data = data == null ? new byte[0] : data;
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.name, MAX_NAME_LENGTH);
        buffer.writeBoolean(this.success);
        buffer.writeUtf(this.message, MAX_MESSAGE_LENGTH);
        buffer.writeByteArray(this.data);
    }

    private static BlueprintToolSaveResultPayload read(final RegistryFriendlyByteBuf buffer) {
        return new BlueprintToolSaveResultPayload(
                buffer.readUtf(MAX_NAME_LENGTH),
                buffer.readBoolean(),
                buffer.readUtf(MAX_MESSAGE_LENGTH),
                buffer.readByteArray(SableSchematicApiPackets.MAX_BLUEPRINT_BYTES)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
