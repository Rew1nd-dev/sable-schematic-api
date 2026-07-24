package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.survival.film.FilmState;
import dev.rew1nd.sableschematicapi.survival.manuscript.BlueprintManuscriptState;
import dev.rew1nd.sableschematicapi.survival.filmprinter.FilmPrinterItemSummary;
import dev.rew1nd.sableschematicapi.survival.filmprinter.FilmPrinterPortableState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SableSchematicApiDataComponents {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SableSchematicApi.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlueprintManuscriptState>>
            BLUEPRINT_MANUSCRIPT_STATE = COMPONENTS.register(
            "blueprint_manuscript_state",
            () -> DataComponentType.<BlueprintManuscriptState>builder()
                    .persistent(BlueprintManuscriptState.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(BlueprintManuscriptState.CODEC))
                    .build()
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilmState>> FILM_STATE =
            COMPONENTS.register(
                    "film_state",
                    () -> DataComponentType.<FilmState>builder()
                            .persistent(FilmState.CODEC)
                            .networkSynchronized(ByteBufCodecs.fromCodec(FilmState.CODEC))
                            .build()
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilmPrinterPortableState>>
            FILM_PRINTER_PORTABLE_STATE = COMPONENTS.register(
            "film_printer_portable_state",
            () -> DataComponentType.<FilmPrinterPortableState>builder()
                    .persistent(FilmPrinterPortableState.CODEC)
                    .build()
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilmPrinterItemSummary>>
            FILM_PRINTER_ITEM_SUMMARY = COMPONENTS.register(
            "film_printer_item_summary",
            () -> DataComponentType.<FilmPrinterItemSummary>builder()
                    .persistent(FilmPrinterItemSummary.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(FilmPrinterItemSummary.CODEC))
                    .build()
    );

    private SableSchematicApiDataComponents() {
    }

    public static void register(final IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
