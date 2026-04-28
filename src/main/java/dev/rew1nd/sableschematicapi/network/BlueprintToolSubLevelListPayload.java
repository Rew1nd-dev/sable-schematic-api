package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BlueprintToolSubLevelListPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<BlueprintToolSubLevelListPayload> TYPE = new Type<>(SableSchematicApi.id("blueprint_tool_sublevel_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintToolSubLevelListPayload> STREAM_CODEC = StreamCodec.ofMember(
            BlueprintToolSubLevelListPayload::write,
            BlueprintToolSubLevelListPayload::read
    );

    public BlueprintToolSubLevelListPayload {
        data = data == null ? new CompoundTag() : data.copy();
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeNbt(this.data);
    }

    private static BlueprintToolSubLevelListPayload read(final RegistryFriendlyByteBuf buffer) {
        final Tag tag = buffer.readNbt(NbtAccounter.create(SableSchematicApiPackets.MAX_ACTION_NBT_BYTES));
        return new BlueprintToolSubLevelListPayload(tag instanceof final CompoundTag compound ? compound : new CompoundTag());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
