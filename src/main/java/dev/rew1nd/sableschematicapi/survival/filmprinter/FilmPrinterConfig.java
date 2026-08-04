package dev.rew1nd.sableschematicapi.survival.filmprinter;

import dev.rew1nd.sableschematicapi.survival.SableServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world server settings for film printing. */
public final class FilmPrinterConfig {
    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK =
            SableServerConfig.FILM_PRINTER_BLOCKS_PER_TICK;

    private FilmPrinterConfig() {
    }
}
