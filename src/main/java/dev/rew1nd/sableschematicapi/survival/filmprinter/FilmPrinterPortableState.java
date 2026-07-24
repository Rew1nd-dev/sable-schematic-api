package dev.rew1nd.sableschematicapi.survival.filmprinter;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;

/** Server-only persistent payload carried by a populated printer item. */
public record FilmPrinterPortableState(CompoundTag data) {
    public static final Codec<FilmPrinterPortableState> CODEC =
            CompoundTag.CODEC.xmap(FilmPrinterPortableState::new, FilmPrinterPortableState::data);

    public FilmPrinterPortableState {
        data = data == null ? new CompoundTag() : data.copy();
    }

    @Override
    public CompoundTag data() {
        return this.data.copy();
    }
}
