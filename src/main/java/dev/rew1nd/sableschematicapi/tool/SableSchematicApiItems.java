package dev.rew1nd.sableschematicapi.tool;

import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.survival.BlueprintDataItem;
import dev.rew1nd.sableschematicapi.survival.SableSchematicApiBlocks;
import dev.rew1nd.sableschematicapi.survival.camera.CameraItem;
import dev.rew1nd.sableschematicapi.survival.SableSchematicApiDataComponents;
import dev.rew1nd.sableschematicapi.survival.film.FilmItem;
import dev.rew1nd.sableschematicapi.survival.film.FilmState;
import dev.rew1nd.sableschematicapi.survival.manuscript.BlueprintManuscriptItem;
import dev.rew1nd.sableschematicapi.survival.manuscript.BlueprintManuscriptState;
import dev.rew1nd.sableschematicapi.survival.filmprinter.FilmPrinterItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SableSchematicApiItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, SableSchematicApi.MODID);

    public static final DeferredHolder<Item, BlueprintToolItem> BLUEPRINT_TOOL = ITEMS.register(
            "blueprint_tool",
            () -> new BlueprintToolItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredHolder<Item, BlueprintDataItem> SURVIVAL_BLUEPRINT = ITEMS.register(
            "survival_blueprint",
            () -> new BlueprintDataItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredHolder<Item, CameraItem> CAMERA = ITEMS.register(
            "camera",
            () -> new CameraItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredHolder<Item, BlueprintManuscriptItem> BLUEPRINT_MANUSCRIPT = ITEMS.register(
            "blueprint_manuscript",
            () -> new BlueprintManuscriptItem(new Item.Properties()
                    .stacksTo(1)
                    .component(
                            SableSchematicApiDataComponents.BLUEPRINT_MANUSCRIPT_STATE.get(),
                            BlueprintManuscriptState.EMPTY
                    ))
    );

    public static final DeferredHolder<Item, FilmItem> FILM = ITEMS.register(
            "film",
            () -> new FilmItem(new Item.Properties()
                    .stacksTo(64)
                    .component(SableSchematicApiDataComponents.FILM_STATE.get(), FilmState.EMPTY))
    );

    public static final DeferredHolder<Item, BlockItem> BLUEPRINT_CANNON = ITEMS.register(
            "blueprint_cannon",
            () -> new BlockItem(SableSchematicApiBlocks.BLUEPRINT_CANNON.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> BLUEPRINT_TABLE = ITEMS.register(
            "blueprint_table",
            () -> new BlockItem(SableSchematicApiBlocks.BLUEPRINT_TABLE.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, FilmPrinterItem> FILM_PRINTER = ITEMS.register(
            "film_printer",
            () -> new FilmPrinterItem(SableSchematicApiBlocks.FILM_PRINTER.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> PROJECTOR = ITEMS.register(
            "projector",
            () -> new BlockItem(SableSchematicApiBlocks.PROJECTOR.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> PROJECTOR_SCREEN = ITEMS.register(
            "projector_screen",
            () -> new BlockItem(SableSchematicApiBlocks.PROJECTOR_SCREEN.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> PROJECTOR_SCREEN_LOCATOR = ITEMS.register(
            "projector_screen_locator",
            () -> new BlockItem(SableSchematicApiBlocks.PROJECTOR_SCREEN_LOCATOR.get(), new Item.Properties())
    );

    private SableSchematicApiItems() {
    }

    public static void register(final IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
