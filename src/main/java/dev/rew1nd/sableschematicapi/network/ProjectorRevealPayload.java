package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ProjectorRevealPayload(byte action,
                                     UUID groupId,
                                     List<UUID> bodyIds,
                                     Vector3d normal,
                                     double startDistance,
                                     double endDistance,
                                     long startGameTime,
                                     long endGameTime,
                                     UUID projectorBodyId,
                                     BlockPos projectorPos,
                                     boolean immediate) implements CustomPacketPayload {
    public static final byte START = 0;
    public static final byte STOP = 1;
    public static final Type<ProjectorRevealPayload> TYPE =
            new Type<>(SableSchematicApi.id("projector_reveal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectorRevealPayload> STREAM_CODEC =
            StreamCodec.ofMember(ProjectorRevealPayload::write, ProjectorRevealPayload::read);

    public ProjectorRevealPayload {
        bodyIds = bodyIds == null ? List.of() : List.copyOf(bodyIds);
        normal = normal == null ? new Vector3d(0, 1, 0) : new Vector3d(normal);
        projectorPos = projectorPos == null ? BlockPos.ZERO : projectorPos.immutable();
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeByte(this.action);
        buffer.writeUUID(this.groupId);
        buffer.writeVarInt(this.bodyIds.size());
        for (final UUID bodyId : this.bodyIds) {
            buffer.writeUUID(bodyId);
        }
        buffer.writeDouble(this.normal.x);
        buffer.writeDouble(this.normal.y);
        buffer.writeDouble(this.normal.z);
        buffer.writeDouble(this.startDistance);
        buffer.writeDouble(this.endDistance);
        buffer.writeLong(this.startGameTime);
        buffer.writeLong(this.endGameTime);
        buffer.writeBoolean(this.projectorBodyId != null);
        if (this.projectorBodyId != null) {
            buffer.writeUUID(this.projectorBodyId);
        }
        buffer.writeBlockPos(this.projectorPos);
        buffer.writeBoolean(this.immediate);
    }

    private static ProjectorRevealPayload read(final RegistryFriendlyByteBuf buffer) {
        final byte action = buffer.readByte();
        final UUID id = buffer.readUUID();
        final int count = buffer.readVarInt();
        if (count < 0 || count > 16384) {
            throw new IllegalArgumentException("Invalid projector body count: " + count);
        }
        final List<UUID> bodies = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            bodies.add(buffer.readUUID());
        }
        return new ProjectorRevealPayload(
                action, id, bodies,
                new Vector3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readDouble(), buffer.readDouble(),
                buffer.readLong(), buffer.readLong(),
                buffer.readBoolean() ? buffer.readUUID() : null,
                buffer.readBlockPos(),
                buffer.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
