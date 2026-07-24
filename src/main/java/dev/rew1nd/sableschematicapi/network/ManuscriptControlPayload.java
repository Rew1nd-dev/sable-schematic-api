package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Small client-to-server manuscript session command. */
public record ManuscriptControlPayload(String action,
                                       UUID token,
                                       String hand,
                                       CompoundTag data) implements CustomPacketPayload {
    private static final int MAX_CONTROL_BYTES = 64 * 1024;
    public static final Type<ManuscriptControlPayload> TYPE =
            new Type<>(SableSchematicApi.id("manuscript_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManuscriptControlPayload> STREAM_CODEC =
            StreamCodec.ofMember(ManuscriptControlPayload::write, ManuscriptControlPayload::read);

    public ManuscriptControlPayload {
        action = action == null ? "" : action;
        hand = hand == null ? "" : hand;
        data = data == null ? new CompoundTag() : data.copy();
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.action, 64);
        buffer.writeBoolean(this.token != null);
        if (this.token != null) {
            buffer.writeUUID(this.token);
        }
        buffer.writeUtf(this.hand, 16);
        buffer.writeNbt(this.data);
    }

    private static ManuscriptControlPayload read(final RegistryFriendlyByteBuf buffer) {
        final String action = buffer.readUtf(64);
        final UUID token = buffer.readBoolean() ? buffer.readUUID() : null;
        final String hand = buffer.readUtf(16);
        final Tag data = buffer.readNbt(NbtAccounter.create(MAX_CONTROL_BYTES));
        return new ManuscriptControlPayload(
                action,
                token,
                hand,
                data instanceof CompoundTag compound ? compound : new CompoundTag()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
