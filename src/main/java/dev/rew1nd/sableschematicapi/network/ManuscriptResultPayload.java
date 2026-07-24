package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Small server response for manuscript session state. */
public record ManuscriptResultPayload(String action,
                                      boolean success,
                                      String reason,
                                      UUID token,
                                      CompoundTag data) implements CustomPacketPayload {
    private static final int MAX_RESULT_BYTES = 64 * 1024;
    public static final Type<ManuscriptResultPayload> TYPE =
            new Type<>(SableSchematicApi.id("manuscript_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManuscriptResultPayload> STREAM_CODEC =
            StreamCodec.ofMember(ManuscriptResultPayload::write, ManuscriptResultPayload::read);

    public ManuscriptResultPayload {
        action = action == null ? "" : action;
        reason = reason == null ? "" : reason;
        data = data == null ? new CompoundTag() : data.copy();
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.action, 64);
        buffer.writeBoolean(this.success);
        buffer.writeUtf(this.reason, 256);
        buffer.writeBoolean(this.token != null);
        if (this.token != null) {
            buffer.writeUUID(this.token);
        }
        buffer.writeNbt(this.data);
    }

    private static ManuscriptResultPayload read(final RegistryFriendlyByteBuf buffer) {
        final String action = buffer.readUtf(64);
        final boolean success = buffer.readBoolean();
        final String reason = buffer.readUtf(256);
        final UUID token = buffer.readBoolean() ? buffer.readUUID() : null;
        final Tag data = buffer.readNbt(NbtAccounter.create(MAX_RESULT_BYTES));
        return new ManuscriptResultPayload(
                action,
                success,
                reason,
                token,
                data instanceof CompoundTag compound ? compound : new CompoundTag()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
