package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record BlueprintToolSaveRequestPayload(String name, Vec3 start, Vec3 end) implements CustomPacketPayload {
    private static final int MAX_NAME_LENGTH = 128;
    public static final Type<BlueprintToolSaveRequestPayload> TYPE = new Type<>(SableSchematicApi.id("blueprint_tool_save_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintToolSaveRequestPayload> STREAM_CODEC = StreamCodec.ofMember(
            BlueprintToolSaveRequestPayload::write,
            BlueprintToolSaveRequestPayload::read
    );

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.name, MAX_NAME_LENGTH);
        writeVec3(buffer, this.start);
        writeVec3(buffer, this.end);
    }

    private static BlueprintToolSaveRequestPayload read(final RegistryFriendlyByteBuf buffer) {
        return new BlueprintToolSaveRequestPayload(
                buffer.readUtf(MAX_NAME_LENGTH),
                readVec3(buffer),
                readVec3(buffer)
        );
    }

    static void writeVec3(final RegistryFriendlyByteBuf buffer, final Vec3 vec) {
        buffer.writeDouble(vec.x);
        buffer.writeDouble(vec.y);
        buffer.writeDouble(vec.z);
    }

    static Vec3 readVec3(final RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
