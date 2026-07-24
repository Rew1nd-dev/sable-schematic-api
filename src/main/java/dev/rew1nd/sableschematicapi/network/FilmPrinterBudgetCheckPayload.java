package dev.rew1nd.sableschematicapi.network;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client hash check for one film-printer material snapshot. */
public record FilmPrinterBudgetCheckPayload(BlockPos pos,
                                            String cachedHash,
                                            boolean forceRefresh) implements CustomPacketPayload {
    public static final Type<FilmPrinterBudgetCheckPayload> TYPE =
            new Type<>(SableSchematicApi.id("film_printer_budget_check"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FilmPrinterBudgetCheckPayload> STREAM_CODEC =
            StreamCodec.ofMember(FilmPrinterBudgetCheckPayload::write, FilmPrinterBudgetCheckPayload::read);

    public FilmPrinterBudgetCheckPayload {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        cachedHash = cachedHash == null ? "" : cachedHash;
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeUtf(this.cachedHash, 64);
        buffer.writeBoolean(this.forceRefresh);
    }

    private static FilmPrinterBudgetCheckPayload read(final RegistryFriendlyByteBuf buffer) {
        return new FilmPrinterBudgetCheckPayload(
                buffer.readBlockPos(),
                buffer.readUtf(64),
                buffer.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
