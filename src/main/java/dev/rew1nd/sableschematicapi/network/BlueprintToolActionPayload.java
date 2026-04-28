package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record BlueprintToolActionPayload(ResourceLocation action, CompoundTag data) implements CustomPacketPayload {
    public static final Type<BlueprintToolActionPayload> TYPE = new Type<>(SableSchematicApi.id("blueprint_tool_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintToolActionPayload> STREAM_CODEC = StreamCodec.ofMember(
            BlueprintToolActionPayload::write,
            BlueprintToolActionPayload::read
    );

    public BlueprintToolActionPayload {
        action = Objects.requireNonNull(action, "action");
        data = data == null ? new CompoundTag() : data.copy();
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(this.action);
        buffer.writeNbt(this.data);
    }

    private static BlueprintToolActionPayload read(final RegistryFriendlyByteBuf buffer) {
        final ResourceLocation action = buffer.readResourceLocation();
        final Tag data = buffer.readNbt(NbtAccounter.create(SableSchematicApiPackets.MAX_ACTION_NBT_BYTES));
        return new BlueprintToolActionPayload(action, data instanceof final CompoundTag compound ? compound : new CompoundTag());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
