package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PhysicsManagerSnapshotPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<PhysicsManagerSnapshotPayload> TYPE =
            new Type<>(SableSchematicApi.id("physics_manager_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhysicsManagerSnapshotPayload> STREAM_CODEC =
            StreamCodec.ofMember(PhysicsManagerSnapshotPayload::write, PhysicsManagerSnapshotPayload::read);

    public PhysicsManagerSnapshotPayload {
        data = data == null ? new CompoundTag() : data.copy();
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeNbt(this.data);
    }

    private static PhysicsManagerSnapshotPayload read(final RegistryFriendlyByteBuf buffer) {
        final Tag data = buffer.readNbt(NbtAccounter.create(SableSchematicApiPackets.MAX_ACTION_NBT_BYTES));
        return new PhysicsManagerSnapshotPayload(
                data instanceof final CompoundTag compound ? compound : new CompoundTag()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
