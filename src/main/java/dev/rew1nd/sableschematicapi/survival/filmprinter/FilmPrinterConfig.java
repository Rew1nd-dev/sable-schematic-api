package dev.rew1nd.sableschematicapi.survival.filmprinter;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world server settings for film printing. */
public final class FilmPrinterConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK = BUILDER
            .comment("Film printer manufacturing work units completed per server tick.")
            .defineInRange("filmPrinter.blocksPerTick", 64, 1, 4096);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private FilmPrinterConfig() {
    }
}
