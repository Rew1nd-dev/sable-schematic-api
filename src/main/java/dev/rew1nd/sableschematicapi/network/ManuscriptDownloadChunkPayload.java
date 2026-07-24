package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** One server-to-client metadata/preview chunk. */
public record ManuscriptDownloadChunkPayload(UUID token,
                                             UUID transferId,
                                             int index,
                                             int totalChunks,
                                             int totalBytes,
                                             String sha256,
                                             byte[] data) implements CustomPacketPayload {
    public static final Type<ManuscriptDownloadChunkPayload> TYPE =
            new Type<>(SableSchematicApi.id("manuscript_download_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManuscriptDownloadChunkPayload> STREAM_CODEC =
            StreamCodec.ofMember(ManuscriptDownloadChunkPayload::write, ManuscriptDownloadChunkPayload::read);

    public ManuscriptDownloadChunkPayload {
        sha256 = sha256 == null ? "" : sha256;
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
        buffer.writeVarInt(this.totalChunks);
        buffer.writeVarInt(this.totalBytes);
        buffer.writeUtf(this.sha256, 64);
        buffer.writeByteArray(this.data);
    }

    private static ManuscriptDownloadChunkPayload read(final RegistryFriendlyByteBuf buffer) {
        return new ManuscriptDownloadChunkPayload(
                buffer.readUUID(),
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(64),
                buffer.readByteArray(ManuscriptUploadChunkPayload.MAX_CHUNK_BYTES)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
