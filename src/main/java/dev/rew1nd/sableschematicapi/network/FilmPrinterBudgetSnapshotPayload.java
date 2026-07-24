package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server response containing a complete, atomically replaceable material snapshot. */
public record FilmPrinterBudgetSnapshotPayload(BlockPos pos,
                                               String snapshotHash,
                                               CompoundTag snapshot) implements CustomPacketPayload {
    public static final Type<FilmPrinterBudgetSnapshotPayload> TYPE =
            new Type<>(SableSchematicApi.id("film_printer_budget_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FilmPrinterBudgetSnapshotPayload> STREAM_CODEC =
            StreamCodec.ofMember(FilmPrinterBudgetSnapshotPayload::write, FilmPrinterBudgetSnapshotPayload::read);

    public FilmPrinterBudgetSnapshotPayload {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        snapshotHash = snapshotHash == null ? "" : snapshotHash;
        snapshot = snapshot == null ? new CompoundTag() : snapshot.copy();
    }

    @Override
    public CompoundTag snapshot() {
        return this.snapshot.copy();
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeUtf(this.snapshotHash, 64);
        buffer.writeNbt(this.snapshot);
    }

    private static FilmPrinterBudgetSnapshotPayload read(final RegistryFriendlyByteBuf buffer) {
        final BlockPos pos = buffer.readBlockPos();
        final String hash = buffer.readUtf(64);
        final Tag tag = buffer.readNbt(NbtAccounter.create(SableSchematicApiPackets.MAX_ACTION_NBT_BYTES));
        return new FilmPrinterBudgetSnapshotPayload(
                pos,
                hash,
                tag instanceof final CompoundTag compound ? compound : new CompoundTag()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
