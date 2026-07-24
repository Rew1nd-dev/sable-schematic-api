package dev.rew1nd.sableschematicapi.survival.filmprinter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Small client-visible tooltip summary; the full portable receipt stays server-only. */
public record FilmPrinterItemSummary(String taskName,
                                     String phase,
                                     long completedWork,
                                     long totalWork,
                                     boolean hasInventory,
                                     int refundItems) {
    public static final Codec<FilmPrinterItemSummary> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("task_name", "").forGetter(FilmPrinterItemSummary::taskName),
            Codec.STRING.optionalFieldOf("phase", FilmPrinterPhase.IDLE.name()).forGetter(FilmPrinterItemSummary::phase),
            Codec.LONG.optionalFieldOf("completed_work", 0L).forGetter(FilmPrinterItemSummary::completedWork),
            Codec.LONG.optionalFieldOf("total_work", 0L).forGetter(FilmPrinterItemSummary::totalWork),
            Codec.BOOL.optionalFieldOf("has_inventory", false).forGetter(FilmPrinterItemSummary::hasInventory),
            Codec.INT.optionalFieldOf("refund_items", 0).forGetter(FilmPrinterItemSummary::refundItems)
    ).apply(instance, FilmPrinterItemSummary::new));
}
