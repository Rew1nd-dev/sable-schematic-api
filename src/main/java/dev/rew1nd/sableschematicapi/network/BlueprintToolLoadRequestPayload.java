package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record BlueprintToolLoadRequestPayload(String name, Vec3 origin, byte[] data) implements CustomPacketPayload {
    private static final int MAX_NAME_LENGTH = 128;
    public static final Type<BlueprintToolLoadRequestPayload> TYPE = new Type<>(SableSchematicApi.id("blueprint_tool_load_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintToolLoadRequestPayload> STREAM_CODEC = StreamCodec.ofMember(
            BlueprintToolLoadRequestPayload::write,
            BlueprintToolLoadRequestPayload::read
    );

    public BlueprintToolLoadRequestPayload {
        name = name == null ? "" : name;
        data = data == null ? new byte[0] : data;
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.name, MAX_NAME_LENGTH);
        BlueprintToolSaveRequestPayload.writeVec3(buffer, this.origin);
        buffer.writeByteArray(this.data);
    }

    private static BlueprintToolLoadRequestPayload read(final RegistryFriendlyByteBuf buffer) {
        return new BlueprintToolLoadRequestPayload(
                buffer.readUtf(MAX_NAME_LENGTH),
                BlueprintToolSaveRequestPayload.readVec3(buffer),
                buffer.readByteArray(SableSchematicApiPackets.MAX_BLUEPRINT_BYTES)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
