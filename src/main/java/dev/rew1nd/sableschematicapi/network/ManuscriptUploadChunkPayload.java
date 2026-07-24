package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** One bounded client-to-server upload chunk. */
public record ManuscriptUploadChunkPayload(UUID token,
                                           UUID transferId,
                                           int index,
                                           byte[] data) implements CustomPacketPayload {
    public static final int MAX_CHUNK_BYTES = 256 * 1024;
    public static final Type<ManuscriptUploadChunkPayload> TYPE =
            new Type<>(SableSchematicApi.id("manuscript_upload_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManuscriptUploadChunkPayload> STREAM_CODEC =
            StreamCodec.ofMember(ManuscriptUploadChunkPayload::write, ManuscriptUploadChunkPayload::read);

    public ManuscriptUploadChunkPayload {
        data = data == null ? new byte[0] : data.clone();
    }

    @Override
    public byte[] data() {
        return this.data.clone();
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(this.token);
        buffer.writeUUID(this.transferId);
        buffer.writeVarInt(this.index);
        buffer.writeByteArray(this.data);
    }

    private static ManuscriptUploadChunkPayload read(final RegistryFriendlyByteBuf buffer) {
        return new ManuscriptUploadChunkPayload(
                buffer.readUUID(),
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readByteArray(MAX_CHUNK_BYTES)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
